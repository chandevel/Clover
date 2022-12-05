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

import static com.github.adamantcheese.chan.Chan.instance;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.misc.TransactionManager;

import java.sql.SQLException;
import java.util.concurrent.*;

public class DatabaseUtils {
    // The database only allows for one connection at a time, so we use this to schedule all database operations.
    private static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();

    /**
     * Summary of the database tables row count, for the developer screen.
     *
     * @return list of all tables and their row count.
     */
    public static String getDatabaseSummary() {
        String o = "";

        DatabaseHelper helper = instance(DatabaseHelper.class);
        try {
            o += "Loadable rows: " + helper.getLoadableDao().countOf() + "\n";
            o += "Pin rows: " + helper.getPinDao().countOf() + "\n";
            o += "SavedReply rows: " + helper.getSavedReplyDao().countOf() + "\n";
            o += "Board rows: " + helper.getBoardDao().countOf() + "\n";
            o += "PostHide rows: " + helper.getPostHideDao().countOf() + "\n";
            o += "Filter rows: " + helper.getFilterDao().countOf() + "\n";
            o += "Site rows: " + helper.getSiteModelDao().countOf() + "\n";
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return o;
    }

    /**
     * Trim a table with the specified trigger and trim count.
     *
     * @param dao     {@link Dao} to use.
     * @param trigger Trim if there are more rows than {@code trigger}.
     * @param trim    Count of rows to trim.
     */
    public static <T, ID> Callable<Void> trimTable(Dao<T, ID> dao, long trigger, long trim) {
        return () -> {
            try {
                long count = dao.countOf();
                if (count > trigger) {
                    dao.executeRawNoArgs("DELETE FROM "
                            + dao.getTableName()
                            + " WHERE id IN (SELECT id FROM "
                            + dao.getTableName()
                            + " ORDER BY id ASC LIMIT "
                            + trim
                            + ")");
                }
            } catch (SQLException e) {
                Logger.w("DatabaseManager", "Error trimming table " + dao.getTableName(), e);
            }
            return null;
        };
    }

    public static <T> void runTaskAsync(final Callable<T> taskCallable) {
        runTaskAsync(taskCallable, result -> {});
    }

    public static <T> void runTaskAsync(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        databaseExecutor.submit(new DatabaseCallable<>(taskCallable, taskResult));
    }

    public static <T> T runTask(final Callable<T> taskCallable) {
        try {
            return databaseExecutor.submit(new DatabaseCallable<>(taskCallable, result -> {})).get();
        } catch (InterruptedException e) {
            // Since we don't rethrow InterruptedException we need to at least restore the
            // "interrupted" flag.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static class DatabaseCallable<T>
            implements Callable<T> {
        private final Callable<T> task;
        private final TaskResult<T> result;

        public DatabaseCallable(Callable<T> task, @NonNull TaskResult<T> result) {
            this.task = task;
            this.result = result;
        }

        @Override
        public T call() {
            try {
                DatabaseHelper databaseHelper = instance(DatabaseHelper.class);
                synchronized (databaseHelper.getConnectionSource()) {
                    final T res = TransactionManager.callInTransaction(databaseHelper.getConnectionSource(), task);
                    BackgroundUtils.runOnMainThread(() -> result.onComplete(res));
                    return res;
                }
            } catch (Exception e) {
                Logger.e(this, "executeTask", e);
                throw new RuntimeException(e);
            }
        }
    }

    public interface TaskResult<T> {
        void onComplete(T result);
    }
}
