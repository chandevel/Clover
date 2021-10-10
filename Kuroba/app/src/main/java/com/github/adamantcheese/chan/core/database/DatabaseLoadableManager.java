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
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.theme.Highlightable;
import com.j256.ormlite.stmt.DeleteBuilder;
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
import java.util.List;
import java.util.Locale;
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
        updateLoadableFields(loadable);
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

            updateLoadableFields(result);
            return result;
        };
    }

    private void updateLoadableFields(Loadable loadable)
            throws SQLException {
        loadable.site = siteRepository.forId(loadable.siteId);
        loadable.board = loadable.site.board(loadable.boardCode);
        loadable.lastLoadDate =
                ChanSettings.showHistory.get() ? GregorianCalendar.getInstance().getTime() : loadable.lastLoadDate;
        helper.getLoadableDao().update(loadable);
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

    /**
     * Called when the application goes into the background, to purge any old loadables that won't be used anymore; keeps
     * the database clean and small. Avoids purging pin loadables.
     */
    public Callable<Void> purgeOld() {
        return () -> {
            DatabaseConnection connection = helper.getLoadableDao().startThreadConnection();
            Calendar oneMonthAgo = GregorianCalendar.getInstance();
            oneMonthAgo.add(Calendar.MONTH, -1);

            DeleteBuilder<Loadable, Integer> builder = helper.getLoadableDao().deleteBuilder();
            builder.where()
                    .lt("lastLoadDate", oneMonthAgo.getTime())
                    .and()
                    .notIn("id", helper.getPinDao().queryBuilder().selectColumns("loadable_id"));
            builder.delete();

            connection.commit(null);
            helper.getLoadableDao().endThreadConnection(connection);
            return null;
        };
    }

    /**
     * @return A callable that "clears" history by setting the last load date to far in the past for all non-pin associated loadables.
     */
    public Callable<Void> clearHistory() {
        return () -> {
            UpdateBuilder<Loadable, Integer> builder =
                    helper.getLoadableDao().updateBuilder().updateColumnValue("lastLoadDate", EPOCH_DATE);
            builder.where().notIn("id", helper.getPinDao().queryBuilder().selectColumns("loadable_id"));
            builder.update();
            return null;
        };
    }

    /**
     * @return A callable that returns a list of history, ignoring pins.
     */
    public Callable<List<History>> getHistory() {
        return () -> {
            List<History> history = new ArrayList<>();
            for (Loadable l : helper.getLoadableDao()
                    .queryBuilder()
                    .orderBy("lastLoadDate", false)
                    .limit(HISTORY_LIMIT)
                    .where()
                    .notIn("id", helper.getPinDao().queryBuilder().selectColumns("loadable_id"))
                    .and()
                    .ne("lastLoadDate", EPOCH_DATE)
                    .query()) {
                l.site = siteRepository.forId(l.siteId);
                l.board = l.site.board(l.boardCode);
                history.add(new History(l));
            }
            return history;
        };
    }

    public static class History
            extends Highlightable {
        public final Loadable loadable;

        public History(Loadable l) {
            loadable = l;
        }
    }
}
