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

import android.annotation.SuppressLint;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.UpdateBuilder;
import com.j256.ormlite.support.DatabaseConnection;

import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

public class DatabaseLoadableManager {
    private final DatabaseHelper helper;
    private final SiteRepository siteRepository;

    private static final long HISTORY_LIMIT = 250L;
    @SuppressLint("ConstantLocale")
    public static final SimpleDateFormat EPOCH_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    public static final Date EPOCH_DATE;

    static {
        Date temp;
        try {
            temp = EPOCH_DATE_FORMAT.parse("1970-01-01 00:00:01");
        } catch (ParseException e) {
            temp = new Date(1000);
        }
        EPOCH_DATE = temp;
    }

    public DatabaseLoadableManager(DatabaseHelper helper, SiteRepository siteRepository) {
        this.helper = helper;
        this.siteRepository = siteRepository;
    }

    /**
     * Called when the application goes into the background, to purge any old loadables that won't be used anymore; keeps
     * the database clean and small.
     */
    public Callable<Void> purgeOld() {
        return () -> {
            DatabaseConnection connection = helper.getLoadableDao().startThreadConnection();
            Calendar oneMonthAgo = GregorianCalendar.getInstance();
            oneMonthAgo.add(Calendar.MONTH, -1);

            Set<Integer> toRemoveLoadableIds = new HashSet<>();

            for (Loadable l : helper.getLoadableDao()
                    .queryBuilder()
                    .selectColumns("id")
                    .where()
                    .lt("lastLoadDate", oneMonthAgo.getTime())
                    .query()) {
                toRemoveLoadableIds.add(l.id);
            }
            for (Pin p : helper.getPinDao().queryBuilder().selectColumns("loadable_id").query()) {
                toRemoveLoadableIds.remove(p.loadable.id);
            }

            helper.getLoadableDao().deleteIds(toRemoveLoadableIds);

            connection.commit(null);
            helper.getLoadableDao().endThreadConnection(connection);
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
            return DatabaseUtils.runTask(getLoadable(loadable));
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
        loadable.site = siteRepository.forId(loadable.siteId);
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

            result.site = siteRepository.forId(result.siteId);
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
            helper.getLoadableDao().delete(siteLoadables);
            return null;
        };
    }

    public Callable<Void> updateLoadable(Loadable updatedLoadable, boolean commit) {
        return () -> {
            if (updatedLoadable.isThreadMode()) {
                if (commit) {
                    DatabaseConnection connection = helper.getLoadableDao().startThreadConnection();
                    helper.getLoadableDao().update(updatedLoadable);
                    connection.commit(null);
                    helper.getLoadableDao().endThreadConnection(connection);
                } else {
                    helper.getLoadableDao().update(updatedLoadable);
                }
            }
            return null;
        };
    }

    public Callable<Void> clearHistory() {
        return () -> {
            Set<Integer> toUpdate = new HashSet<>();
            for (Loadable l : helper.getLoadableDao().queryBuilder().selectColumns("id").query()) {
                toUpdate.add(l.id);
            }
            for (Pin p : helper.getPinDao().queryBuilder().selectColumns("loadable_id").query()) {
                toUpdate.remove(p.loadable.id);
            }

            UpdateBuilder<Loadable, Integer> builder =
                    helper.getLoadableDao().updateBuilder().updateColumnValue("lastLoadDate", EPOCH_DATE);
            builder.where().in("id", toUpdate);
            builder.update();
            return null;
        };
    }

    public Callable<List<History>> getHistory() {
        return () -> {
            Set<Integer> historyLoadableIds = new HashSet<>();
            for (Loadable l : helper.getLoadableDao()
                    .queryBuilder()
                    .selectColumns("id")
                    .orderBy("lastLoadDate", false)
                    .limit(HISTORY_LIMIT)
                    .where()
                    .ne("lastLoadDate", EPOCH_DATE)
                    .query()) {
                historyLoadableIds.add(l.id);
            }
            for (Pin p : helper.getPinDao().queryBuilder().selectColumns("loadable_id").query()) {
                historyLoadableIds.remove(p.loadable.id);
            }

            List<History> history = new ArrayList<>();
            for (Loadable l : helper.getLoadableDao().queryBuilder().where().in("id", historyLoadableIds).query()) {
                l.site = siteRepository.forId(l.siteId);
                l.board = l.site.board(l.boardCode);
                history.add(new History(l));
            }
            return history;
        };
    }

    public static class History {
        public Loadable loadable;
        public boolean highlighted;

        public History(Loadable l) {
            loadable = l;
        }
    }
}
