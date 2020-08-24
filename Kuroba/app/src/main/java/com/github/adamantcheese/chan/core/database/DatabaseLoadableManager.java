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

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;

public class DatabaseLoadableManager {
    @Inject
    DatabaseHelper helper;

    public DatabaseLoadableManager() {
        inject(this);
    }

    /**
     * Called when the application goes into the background, to purge any old loadables that won't be used anymore; keeps
     * the database clean and small.
     */
    public Callable<Void> purgeOld() {
        return () -> {
            Calendar oneMonthAgo = GregorianCalendar.getInstance();
            oneMonthAgo.add(Calendar.MONTH, -1);
            DeleteBuilder<Loadable, Integer> delete = helper.getLoadableDao().deleteBuilder();
            delete.where().lt("lastLoadDate", oneMonthAgo.getTime()).prepare();
            delete.delete();
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
        if (loadable.isCatalogMode()) {
            return loadable;
        } else {
            return Chan.instance(DatabaseManager.class).runTask(getLoadable(loadable));
        }
    }

    /**
     * Call this when you use a thread loadable as a foreign object on your table
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

        // refresh contents
        helper.getLoadableDao().refresh(loadable);
        loadable.site = instance(SiteRepository.class).forId(loadable.siteId);
        loadable.board = loadable.site.board(loadable.boardCode);
        loadable.lastLoadDate = GregorianCalendar.getInstance().getTime();
        helper.getLoadableDao().update(loadable);
        return loadable;
    }

    private Callable<Loadable> getLoadable(final Loadable loadable) {
        return () -> {
            QueryBuilder<Loadable, Integer> builder = helper.getLoadableDao().queryBuilder();
            List<Loadable> results = builder.where()
                    .eq("site", loadable.siteId)
                    .and()
                    .eq("mode", loadable.mode)
                    .and()
                    .eq("board", loadable.boardCode)
                    .and()
                    .eq("no", loadable.no)
                    .query();

            Loadable result = results.isEmpty() ? loadable : results.get(0);
            if (results.isEmpty()) {
                helper.getLoadableDao().create(loadable);
            }

            result.site = instance(SiteRepository.class).forId(result.siteId);
            result.board = result.site.board(result.boardCode);
            result.lastLoadDate = GregorianCalendar.getInstance().getTime();
            helper.getLoadableDao().update(result);
            return result;
        };
    }

    public Callable<List<Loadable>> getLoadables(Site site) {
        return () -> helper.getLoadableDao().queryForEq("site", site.id());
    }

    public Callable<Object> deleteLoadables(List<Loadable> siteLoadables) {
        return () -> {
            Set<Integer> loadableIdSet = new HashSet<>();

            for (Loadable loadable : siteLoadables) {
                loadableIdSet.add(loadable.id);
            }

            DeleteBuilder<Loadable, Integer> builder = helper.getLoadableDao().deleteBuilder();
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
            if (updatedLoadable.isThreadMode()) {
                helper.getLoadableDao().update(updatedLoadable);
            }
            return null;
        };
    }

    public Callable<List<Loadable>> getHistory() {
        return () -> {
            List<Loadable> history = helper.getLoadableDao().queryBuilder().orderBy("lastLoadDate", false).query();
            for (Loadable l : history) {
                l.site = instance(SiteRepository.class).forId(l.siteId);
                l.board = l.site.board(l.boardCode);
            }
            return history;
        };
    }
}
