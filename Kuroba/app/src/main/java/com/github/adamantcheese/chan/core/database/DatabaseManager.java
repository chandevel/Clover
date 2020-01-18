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

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.misc.TransactionManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * The central point for database related access.<br>
 * <b>All database queries are run on a single database thread</b>, therefor all functions return a
 * {@link Callable} that needs to be queued on either {@link #runTaskAsync(Callable)},
 * {@link #runTaskAsync(Callable, TaskResult)} or {@link #runTask(Callable)}.<br>
 * You often want the sync flavour for queries that return data, it waits for the task to be finished on the other thread.<br>
 * Use the async versions when you don't care when the query is done.
 */
public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    private final ExecutorService backgroundExecutor;
    private Thread executorThread;

    @Inject
    DatabaseHelper helper;

    private final DatabasePinManager databasePinManager;
    private final DatabaseLoadableManager databaseLoadableManager;
    private final DatabaseHistoryManager databaseHistoryManager;
    private final DatabaseSavedReplyManager databaseSavedReplyManager;
    private final DatabaseFilterManager databaseFilterManager;
    private final DatabaseBoardManager databaseBoardManager;
    private final DatabaseSiteManager databaseSiteManager;
    private final DatabaseHideManager databaseHideManager;
    private final DatabaseSavedThreadManager databaseSavedThreadManager;

    @Inject
    public DatabaseManager() {
        inject(this);

        backgroundExecutor = new ThreadPoolExecutor(1, 1, 1000L, TimeUnit.DAYS, new LinkedBlockingQueue<>());

        databaseLoadableManager = new DatabaseLoadableManager();
        databasePinManager = new DatabasePinManager(databaseLoadableManager);
        databaseHistoryManager = new DatabaseHistoryManager(databaseLoadableManager);
        databaseSavedReplyManager = new DatabaseSavedReplyManager();
        databaseFilterManager = new DatabaseFilterManager();
        databaseBoardManager = new DatabaseBoardManager();
        databaseSiteManager = new DatabaseSiteManager();
        databaseHideManager = new DatabaseHideManager();
        databaseSavedThreadManager = new DatabaseSavedThreadManager();
        EventBus.getDefault().register(this);
    }

    public void initializeAndTrim() {
        // Loads data into fields.
        runTask(databaseSavedReplyManager.load());

        // Only trims.
        runTaskAsync(databaseHistoryManager.load());
        runTaskAsync(databaseHideManager.load());
    }

    public DatabasePinManager getDatabasePinManager() {
        return databasePinManager;
    }

    public DatabaseLoadableManager getDatabaseLoadableManager() {
        return databaseLoadableManager;
    }

    public DatabaseHistoryManager getDatabaseHistoryManager() {
        return databaseHistoryManager;
    }

    public DatabaseSavedReplyManager getDatabaseSavedReplyManager() {
        return databaseSavedReplyManager;
    }

    public DatabaseFilterManager getDatabaseFilterManager() {
        return databaseFilterManager;
    }

    public DatabaseBoardManager getDatabaseBoardManager() {
        return databaseBoardManager;
    }

    public DatabaseSiteManager getDatabaseSiteManager() {
        return databaseSiteManager;
    }

    public DatabaseHideManager getDatabaseHideManager() {
        return databaseHideManager;
    }

    public DatabaseSavedThreadManager getDatabaseSavedThreadManager() {
        return databaseSavedThreadManager;
    }
    // Called when the app changes foreground state

    @Subscribe
    public void onEvent(Chan.ForegroundChangedMessage message) {
        if (!message.inForeground) {
            runTaskAsync(databaseLoadableManager.flush());
        }
    }

    /**
     * Reset all tables in the database. Used for the developer screen.
     */
    public void reset() {
        helper.reset();
        initializeAndTrim();
    }

    /**
     * Summary of the database tables row count, for the developer screen.
     *
     * @return list of all tables and their row count.
     */
    public String getSummary() {
        String o = "";

        try {
            o += "Loadable rows: " + helper.loadableDao.countOf() + "\n";
            o += "Pin rows: " + helper.pinDao.countOf() + "\n";
            o += "SavedReply rows: " + helper.savedDao.countOf() + "\n";
            o += "Board rows: " + helper.boardsDao.countOf() + "\n";
            o += "PostHide rows: " + helper.postHideDao.countOf() + "\n";
            o += "History rows: " + helper.historyDao.countOf() + "\n";
            o += "Filter rows: " + helper.filterDao.countOf() + "\n";
            o += "Site rows: " + helper.siteDao.countOf() + "\n";
            o += "Local thread rows: " + helper.savedThreadDao.countOf() + "\n";
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return o;
    }

    /**
     * Trim a table with the specified trigger and trim count.
     *
     * @param dao     {@link Dao} to use.
     * @param table   name of the table, used in the query (not escaped).
     * @param trigger Trim if there are more rows than {@code trigger}.
     * @param trim    Count of rows to trim.
     */
    /*package*/ void trimTable(Dao dao, String table, long trigger, long trim) {
        try {
            long count = dao.countOf();
            if (count > trigger) {
                dao.executeRaw(
                        "DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " ORDER BY id ASC LIMIT ?)",
                        String.valueOf(trim)
                );
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error trimming table " + table, e);
        }
    }

    public <T> void runTaskAsync(final Callable<T> taskCallable) {
        runTaskAsync(taskCallable, result -> {
        });
    }

    public <T> void runTaskAsync(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        executeTask(taskCallable, taskResult);
    }

    public <T> T runTask(final Callable<T> taskCallable) {
        try {
            return executeTask(taskCallable, null).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Future<T> executeTask(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        if (Thread.currentThread() == executorThread) {
            DatabaseCallable<T> databaseCallable = new DatabaseCallable<>(taskCallable, taskResult);
            T result = databaseCallable.call();

            return new Future<T>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public T get() {
                    return result;
                }

                @Override
                public T get(long timeout, @NonNull TimeUnit unit) {
                    return result;
                }
            };
        } else {
            return backgroundExecutor.submit(new DatabaseCallable<>(taskCallable, taskResult));
        }
    }

    private class DatabaseCallable<T>
            implements Callable<T> {
        private final Callable<T> taskCallable;
        private final TaskResult<T> taskResult;

        public DatabaseCallable(Callable<T> taskCallable, TaskResult<T> taskResult) {
            this.taskCallable = taskCallable;
            this.taskResult = taskResult;
        }

        @Override
        public T call() {
            executorThread = Thread.currentThread();

            try {
                final T result = TransactionManager.callInTransaction(helper.getConnectionSource(), taskCallable);
                if (taskResult != null) {
                    new Handler(Looper.getMainLooper()).post(() -> taskResult.onComplete(result));
                }
                return result;
            } catch (Exception e) {
                Logger.e(TAG, "executeTask", e);
                throw new RuntimeException(e);
            }
        }
    }

    public interface TaskResult<T> {
        void onComplete(T result);
    }
}
