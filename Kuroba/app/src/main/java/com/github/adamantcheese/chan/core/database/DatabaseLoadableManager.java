/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.database;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static java.util.concurrent.TimeUnit.DAYS;

public class DatabaseLoadableManager {
    @Inject
    DatabaseHelper helper;

    // This map converts a loadable without an ID to a loadable with an ID, which is obtained from the database
    // Loadables without an ID are gotten from Loadable factory methods and must be put into the database before having
    // an ID, which is used by other data items for indexing and whatnot
    private Map<Loadable, Loadable> cachedLoadables = new HashMap<>();

    public DatabaseLoadableManager() {
        inject(this);
    }

    public int cacheSize() {
        return cachedLoadables.size();
    }

    /**
     * Called when the application goes into the background, to do intensive update calls for loadables
     * whose list indexes or titles have changed.
     */
    public Callable<Void> flush() {
        return () -> {
            for (Loadable loadable : cachedLoadables.values()) {
                if (loadable.dirty) {
                    loadable.dirty = false;
                    helper.loadableDao.update(loadable);
                }
            }
            // if we haven't purged loadables yet and we're on the first day of the month, then
            // purge loadables that haven'tbeen loaded for more than one month
            int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            if (!PersistableChanState.loadablesPurged.get() && dayOfMonth == 1) {
                List<Loadable> allLoadables = helper.loadableDao.queryForAll();
                for (Loadable l : allLoadables) {
                    if (l.lastLoadDate.getTime() + DAYS.toMillis(30) < System.currentTimeMillis()) {
                        helper.loadableDao.delete(l);
                    }
                }
                PersistableChanState.loadablesPurged.set(true);
            } else if (dayOfMonth > 1) {
                PersistableChanState.loadablesPurged.set(false);
            }
            return null;
        };
    }

    /**
     * All loadables that are not gotten from a database (like from any of the Loadable.for...() factory methods)
     * need to go through this method to correctly get a loadable if it already existed in the db.
     * <p>It will search the database for existing loadables of the mode is THREAD, and return one of those if there is
     * else it will create the loadable in the database and return the given loadable.
     *
     * @param loadable Loadable to search from that was not yet gotten from the db.
     * @return a loadable ready to use.
     */
    public Loadable get(final Loadable loadable) {
        if (loadable.id != 0) {
            return loadable;
        }

        // We only cache THREAD loadables in the db
        if (loadable.isThreadMode()) {
            return Chan.instance(DatabaseManager.class).runTask(getLoadable(loadable));
        } else {
            return loadable;
        }
    }

    /**
     * Call this when you use a thread loadable as a foreign object on your table
     * <p>It will correctly update the loadable cache
     *
     * @param loadable Loadable that only has its id loaded
     * @return a loadable ready to use.
     *
     * @throws SQLException database error
     */
    public Loadable refreshForeign(final Loadable loadable)
            throws SQLException {
        if (loadable.id == 0) {
            throw new IllegalArgumentException("This only works loadables that have their id loaded");
        }

        // If the loadable was already loaded in the cache, return that entry
        Loadable cachedLoadable = cachedLoadables.get(loadable);
        if (cachedLoadable != null) {
            cachedLoadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
            cachedLoadable.dirty = true;
            return cachedLoadable;
        }

        // Add it to the cache, refresh contents
        helper.loadableDao.refresh(loadable);
        loadable.site = instance(SiteRepository.class).forId(loadable.siteId);
        loadable.board = loadable.site.board(loadable.boardCode);
        loadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
        loadable.dirty = true;
        cachedLoadables.put(loadable, loadable);
        return loadable;
    }

    private Callable<Loadable> getLoadable(final Loadable loadable) {
        if (!loadable.isThreadMode()) {
            return () -> loadable;
        }

        return () -> {
            Loadable cachedLoadable = cachedLoadables.get(loadable);
            if (cachedLoadable != null) {
                Logger.v(DatabaseLoadableManager.this, "Cached loadable found " + cachedLoadable);
                cachedLoadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
                cachedLoadable.dirty = true;
                return cachedLoadable;
            } else {
                QueryBuilder<Loadable, Integer> builder = helper.loadableDao.queryBuilder();
                List<Loadable> results = builder.where()
                        .eq("site", loadable.siteId)
                        .and()
                        .eq("mode", loadable.mode)
                        .and()
                        .eq("board", loadable.boardCode)
                        .and()
                        .eq("no", loadable.no)
                        .query();

                if (results.size() > 1) {
                    Logger.w(
                            DatabaseLoadableManager.this,
                            "Multiple loadables found for where Loadable.equals() would return true"
                    );
                    for (Loadable result : results) {
                        Logger.w(DatabaseLoadableManager.this, result.toString());
                    }
                }

                Loadable result = results.isEmpty() ? null : results.get(0);
                if (result == null) {
                    helper.loadableDao.create(loadable);
                    Logger.d(DatabaseLoadableManager.this, "Created loadable " + loadable);
                    result = loadable;
                } else {
                    Logger.d(DatabaseLoadableManager.this, "Loadable found in db");
                    result.site = instance(SiteRepository.class).forId(result.siteId);
                    result.board = result.site.board(result.boardCode);
                }

                cachedLoadables.put(result, result);
                result.lastLoadDate = GregorianCalendar.getInstance().getTime();
                result.dirty = true;
                return result;
            }
        };
    }

    public Callable<List<Loadable>> getLoadables(Site site) {
        return () -> {
            List<Loadable> loadables = helper.loadableDao.queryForEq("site", site.id());
            for (Loadable l : loadables) {
                l.lastLoadDate = GregorianCalendar.getInstance().getTime();
                l.dirty = true;
            }
            return loadables;
        };
    }

    public Callable<Object> deleteLoadables(List<Loadable> siteLoadables) {
        return () -> {
            Set<Integer> loadableIdSet = new HashSet<>();

            for (Loadable loadable : siteLoadables) {
                loadableIdSet.add(loadable.id);
            }

            DeleteBuilder<Loadable, Integer> builder = helper.loadableDao.deleteBuilder();
            builder.where().in("id", loadableIdSet);

            int deletedCount = builder.delete();
            if (loadableIdSet.size() != deletedCount) {
                throw new IllegalStateException(
                        "Deleted count didn't equal loadableIdSet.size(). (deletedCount = " + deletedCount + "), "
                                + "(loadableIdSet = " + loadableIdSet.size() + ")");
            }

            return null;
        };
    }

    public Callable<Void> updateLoadable(Loadable updatedLoadable) {
        return () -> {
            for (Loadable key : cachedLoadables.keySet()) {
                if (key.id == updatedLoadable.id) {
                    cachedLoadables.remove(key);
                    cachedLoadables.put(key, updatedLoadable);
                    break;
                }
            }

            updatedLoadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
            helper.loadableDao.update(updatedLoadable);
            return null;
        };
    }
}
