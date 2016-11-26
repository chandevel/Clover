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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.Chan;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.ThreadHide;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import de.greenrobot.event.EventBus;

/**
 * The central point for database related access.<br>
 * <b>All database queries are run on a single database thread</b>, therefor all functions return a
 * {@link Callable} that needs to be queued on either {@link #runTask(Callable)},
 * {@link #runTask(Callable, TaskResult)} or {@link #runTaskSync(Callable)}.<br>
 * You often want the sync flavour for queries that return data, it waits for the task to be finished on the other thread.<br>
 * Use the async versions when you don't care when the query is done.
 */
public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    private static final long THREAD_HIDE_TRIM_TRIGGER = 250;
    private static final long THREAD_HIDE_TRIM_COUNT = 50;

    private final ExecutorService backgroundExecutor;
    private final DatabaseHelper helper;

    private final List<ThreadHide> threadHides = new ArrayList<>();
    private final HashSet<Integer> threadHidesIds = new HashSet<>();

    private final DatabasePinManager databasePinManager;
    private final DatabaseLoadableManager databaseLoadableManager;
    private final DatabaseHistoryManager databaseHistoryManager;
    private final DatabaseSavedReplyManager databaseSavedReplyManager;
    private final DatabaseFilterManager databaseFilterManager;
    private final DatabaseBoardManager databaseBoardManager;

    public DatabaseManager(Context context) {
        backgroundExecutor = Executors.newSingleThreadExecutor();

        helper = new DatabaseHelper(context);
        databaseLoadableManager = new DatabaseLoadableManager(this, helper);
        databasePinManager = new DatabasePinManager(this, helper, databaseLoadableManager);
        databaseHistoryManager = new DatabaseHistoryManager(this, helper, databaseLoadableManager);
        databaseSavedReplyManager = new DatabaseSavedReplyManager(this, helper);
        databaseFilterManager = new DatabaseFilterManager(this, helper);
        databaseBoardManager = new DatabaseBoardManager(this, helper);
        initialize();
        EventBus.getDefault().register(this);
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

    // Called when the app changes foreground state
    public void onEvent(Chan.ForegroundChangedMessage message) {
        if (!message.inForeground) {
            runTask(databaseLoadableManager.flush());
        }
    }

    private void initialize() {
        loadThreadHides();
        runTaskSync(databaseHistoryManager.load());
        runTaskSync(databaseSavedReplyManager.load());
    }

    /**
     * Reset all tables in the database. Used for the developer screen.
     */
    public void reset() {
        helper.reset();
        initialize();
    }

    /**
     * Check if the post is added in the threadhide table.
     *
     * @param post Post to check the board and no on
     * @return true if it was hidden, false otherwise
     */
    public boolean isThreadHidden(Post post) {
        if (threadHidesIds.contains(post.no)) {
            for (ThreadHide hide : threadHides) {
                if (hide.no == post.no && hide.board.equals(post.boardId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Adds an entry to the threadhide table and updates any caching members.
     *
     * @param threadHide The {@link ThreadHide} to add.
     */
    public void addThreadHide(ThreadHide threadHide) {
        try {
            helper.threadHideDao.create(threadHide);
            threadHides.add(threadHide);
            threadHidesIds.add(threadHide.no);
        } catch (SQLException e) {
            Logger.e(TAG, "Error adding threadhide", e);
        }
    }

    /**
     * Removes the entry from the threadhide table and updates any caching members.
     *
     * @param threadHide The {@link ThreadHide} to remove.
     */
    public void removeThreadHide(ThreadHide threadHide) {
        try {
            helper.threadHideDao.delete(threadHide);
            threadHides.remove(threadHide);
            // ThreadHidesIds not removed because there may be another post with the same id on another board
            // It's just an caching thing, it'll reset itself after a restart
        } catch (SQLException e) {
            Logger.e(TAG, "Error deleting threadhide", e);
        }
    }

    /**
     * Clears all {@link ThreadHide}s from the table and resets any caching members.
     */
    public void clearAllThreadHides() {
        try {
            TableUtils.clearTable(helper.getConnectionSource(), ThreadHide.class);
            threadHides.clear();
            threadHidesIds.clear();
        } catch (SQLException e) {
            Logger.e(TAG, "Error clearing threadhide table", e);
        }
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
            o += "ThreadHide rows: " + helper.threadHideDao.countOf() + "\n";
            o += "History rows: " + helper.historyDao.countOf() + "\n";
            o += "Filter rows: " + helper.filterDao.countOf() + "\n";
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return o;
    }

    private void loadThreadHides() {
        try {
            trimTable(helper.threadHideDao, "threadhide", THREAD_HIDE_TRIM_TRIGGER, THREAD_HIDE_TRIM_COUNT);

            threadHides.clear();
            threadHides.addAll(helper.threadHideDao.queryForAll());
            threadHidesIds.clear();
            for (ThreadHide hide : threadHides) {
                threadHidesIds.add(hide.no);
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error loading thread hides", e);
        }
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
                long start = Time.startTiming();
                dao.executeRaw("DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " ORDER BY id ASC LIMIT ?)", String.valueOf(trim));
                Time.endTiming("Trimmed " + table + " from " + count + " to " + dao.countOf() + " rows", start);
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error trimming table " + table, e);
        }
    }

    public <T> void runTask(final Callable<T> taskCallable) {
        runTask(taskCallable, null);
    }

    public <T> void runTask(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        executeTask(taskCallable, taskResult);
    }

    public <T> T runTaskSync(final Callable<T> taskCallable) {
        try {
            return executeTask(taskCallable, null).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Future<T> executeTask(final Callable<T> taskCallable, final TaskResult<T> taskResult) {
        return backgroundExecutor.submit(new Callable<T>() {
            @Override
            public T call() {
                try {
                    final T result = TransactionManager.callInTransaction(helper.getConnectionSource(), taskCallable);
                    if (taskResult != null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                taskResult.onComplete(result);
                            }
                        });
                    }
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public interface TaskResult<T> {
        void onComplete(T result);
    }
}
