/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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

import android.util.Log;

import com.j256.ormlite.stmt.QueryBuilder;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.utils.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseLoadableManager {
    private static final String TAG = "DatabaseLoadableManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    private Map<Loadable, Loadable> cachedLoadables = new HashMap<>();

    public DatabaseLoadableManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    /**
     * Called when the application goes into the background, to do intensive update calls for loadables
     * whose list indexes or titles have changed.
     */
    public Callable<Void> flush() {
        return () -> {
            List<Loadable> toFlush = new ArrayList<>();
            for (Loadable loadable : cachedLoadables.values()) {
                if (loadable.dirty) {
                    loadable.dirty = false;
                    toFlush.add(loadable);
                }
            }

            if (!toFlush.isEmpty()) {
                Logger.d(TAG, "Flushing " + toFlush.size() + " loadable(s)");
                for (int i = 0; i < toFlush.size(); i++) {
                    Loadable loadable = toFlush.get(i);
                    helper.loadableDao.update(loadable);
                }
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
            throw new IllegalArgumentException("get() only works for transient loadables");
        }

        // We only cache THREAD loadables in the db
        if (loadable.isThreadMode()) {
            return databaseManager.runTask(getLoadable(loadable));
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
     * @throws SQLException
     */
    public Loadable refreshForeign(final Loadable loadable) throws SQLException {
        if (loadable.id == 0) {
            throw new IllegalArgumentException("This only works loadables that have their id loaded");
        }

        // If the loadable was already loaded in the cache, return that entry
        for (Loadable key : cachedLoadables.keySet()) {
            if (key.id == loadable.id) {
                return key;
            }
        }

        // Add it to the cache, refresh contents
        helper.loadableDao.refresh(loadable);
        loadable.site = SiteRepository.forId(loadable.siteId);
        loadable.board = loadable.site.board(loadable.boardCode);
        cachedLoadables.put(loadable, loadable);
        return loadable;
    }

    private Callable<Loadable> getLoadable(final Loadable loadable) {
        if (!loadable.isThreadMode()) {
            throw new IllegalArgumentException("getLoadable can only be used for thread loadables");
        }

        return () -> {
            Loadable cachedLoadable = cachedLoadables.get(loadable);
            if (cachedLoadable != null) {
                Logger.v(TAG, "Cached loadable found");
                return cachedLoadable;
            } else {
                QueryBuilder<Loadable, Integer> builder = helper.loadableDao.queryBuilder();
                List<Loadable> results = builder.where()
                        .eq("site", loadable.siteId).and()
                        .eq("mode", loadable.mode)
                        .and().eq("board", loadable.boardCode)
                        .and().eq("no", loadable.no)
                        .query();

                if (results.size() > 1) {
                    Log.w(TAG, "Multiple loadables found for where Loadable.equals() would return true");
                    for (Loadable result : results) {
                        Log.w(TAG, result.toString());
                    }
                }

                Loadable result = results.isEmpty() ? null : results.get(0);
                if (result == null) {
                    Log.d(TAG, "Creating loadable");
                    helper.loadableDao.create(loadable);
                    result = loadable;
                } else {
                    Log.d(TAG, "Loadable found in db");
                    result.site = SiteRepository.forId(result.siteId);
                    result.board = result.site.board(result.boardCode);
                }

                cachedLoadables.put(result, result);
                return result;
            }
        };
    }
}
