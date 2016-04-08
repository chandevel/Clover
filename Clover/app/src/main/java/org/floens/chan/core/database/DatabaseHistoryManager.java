/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.database;

import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.History;
import org.floens.chan.utils.Time;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseHistoryManager {
    private static final String TAG = "DatabaseHistoryManager";

    private static final long HISTORY_TRIM_TRIGGER = 100;
    private static final long HISTORY_TRIM_COUNT = 50;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;
    private DatabaseLoadableManager databaseLoadableManager;

    public DatabaseHistoryManager(DatabaseManager databaseManager, DatabaseHelper helper, DatabaseLoadableManager databaseLoadableManager) {
        this.databaseManager = databaseManager;
        this.helper = helper;
        this.databaseLoadableManager = databaseLoadableManager;
    }

    public void add(History history) {
        databaseManager.runTaskSync(addHistory(history));
    }

    public Callable<Void> load() {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                databaseManager.trimTable(helper.historyDao, "history", HISTORY_TRIM_TRIGGER, HISTORY_TRIM_COUNT);

                return null;
            }
        };
    }

    public Callable<List<History>> getHistory() {
        return new Callable<List<History>>() {
            @Override
            public List<History> call() throws Exception {
                QueryBuilder<History, Integer> historyQuery = helper.historyDao.queryBuilder();
                List<History> date = historyQuery.orderBy("date", false).query();
                for (int i = 0; i < date.size(); i++) {
                    History history = date.get(i);
                    history.loadable = databaseLoadableManager.refreshForeign(history.loadable);
                }
                return date;
            }
        };
    }

    public Callable<History> addHistory(final History history) {
        if (!history.loadable.isThreadMode()) {
            throw new IllegalArgumentException("History loadables must be in thread mode");
        }

        if (history.loadable.id == 0) {
            throw new IllegalArgumentException("History loadable is not yet in the db");
        }

        return new Callable<History>() {
            @Override
            public History call() throws Exception {
                QueryBuilder<History, Integer> builder = helper.historyDao.queryBuilder();
                List<History> existingHistories = builder.where().eq("loadable_id", history.loadable.id).query();
                History existingHistoryForLoadable = existingHistories.isEmpty() ? null : existingHistories.get(0);

                if (existingHistoryForLoadable != null) {
                    existingHistoryForLoadable.date = Time.get();
                    helper.historyDao.update(existingHistoryForLoadable);
                } else {
                    history.date = Time.get();
                    helper.historyDao.create(history);
                }

                return history;
            }
        };
    }

    public Callable<Void> removeHistory(final History history) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                helper.historyDao.delete(history);
                return null;
            }
        };
    }

    public Callable<Void> clearHistory() {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long start = Time.startTiming();
                TableUtils.clearTable(helper.getConnectionSource(), History.class);
                Time.endTiming("Clear history table", start);

                return null;
            }
        };
    }
}
