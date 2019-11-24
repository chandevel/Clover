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
import com.github.adamantcheese.chan.core.model.orm.History;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseHistoryManager {
    private static final String TAG = "DatabaseHistoryManager";

    private static final long HISTORY_TRIM_TRIGGER = 100;
    private static final long HISTORY_TRIM_COUNT = 50;

    @Inject
    DatabaseHelper helper;
    private DatabaseLoadableManager databaseLoadableManager;

    public DatabaseHistoryManager(DatabaseLoadableManager databaseLoadableManager) {
        inject(this);
        this.databaseLoadableManager = databaseLoadableManager;
    }

    public Callable<Void> load() {
        return () -> {
            Chan.injector()
                .instance(DatabaseManager.class)
                .trimTable(helper.historyDao, "history", HISTORY_TRIM_TRIGGER, HISTORY_TRIM_COUNT);

            return null;
        };
    }

    public Callable<List<History>> getHistory() {
        return () -> {
            QueryBuilder<History, Integer> historyQuery = helper.historyDao.queryBuilder();
            List<History> date = historyQuery.orderBy("date", false).query();
            for (History history : date) {
                history.loadable = databaseLoadableManager.refreshForeign(history.loadable);
            }
            return date;
        };
    }

    public Callable<History> addHistory(final History history) {
        if (!history.loadable.isThreadMode()) {
            throw new IllegalArgumentException("History loadables must be in thread mode");
        }

        if (history.loadable.id == 0) {
            throw new IllegalArgumentException("History loadable is not yet in the db");
        }

        return () -> {
            QueryBuilder<History, Integer> builder = helper.historyDao.queryBuilder();
            List<History> existingHistories = builder.where().eq("loadable_id", history.loadable.id).query();
            History existingHistoryForLoadable = existingHistories.isEmpty() ? null : existingHistories.get(0);

            if (existingHistoryForLoadable != null) {
                existingHistoryForLoadable.date = System.currentTimeMillis();
                helper.historyDao.update(existingHistoryForLoadable);
            } else {
                history.date = System.currentTimeMillis();
                helper.historyDao.create(history);
            }

            return history;
        };
    }

    public Callable<Void> removeHistory(final History history) {
        return () -> {
            helper.historyDao.delete(history);
            return null;
        };
    }

    public Callable<Void> clearHistory() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), History.class);
            return null;
        };
    }

    public Callable<Void> deleteHistory(List<Loadable> siteLoadables) {
        return () -> {
            Set<Integer> loadableIdSet = new HashSet<>();

            for (Loadable loadable : siteLoadables) {
                loadableIdSet.add(loadable.id);
            }

            DeleteBuilder<History, Integer> builder = helper.historyDao.deleteBuilder();
            builder.where().in("loadable_id", loadableIdSet);
            builder.delete();

            return null;
        };
    }
}
