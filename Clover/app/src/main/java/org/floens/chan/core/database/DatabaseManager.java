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

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.History;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.model.ThreadHide;
import org.floens.chan.utils.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.j256.ormlite.misc.TransactionManager.callInTransaction;

public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    private static final long SAVED_REPLY_TRIM_TRIGGER = 250;
    private static final long SAVED_REPLY_TRIM_COUNT = 50;
    private static final long THREAD_HIDE_TRIM_TRIGGER = 250;
    private static final long THREAD_HIDE_TRIM_COUNT = 50;
    private static final long HISTORY_TRIM_TRIGGER = 500;
    private static final long HISTORY_TRIM_COUNT = 50;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    private final DatabaseHelper helper;

    private final Object savedRepliesLock = new Object();
    private final List<SavedReply> savedReplies = new ArrayList<>();
    private final HashSet<Integer> savedRepliesIds = new HashSet<>();

    private final List<ThreadHide> threadHides = new ArrayList<>();
    private final HashSet<Integer> threadHidesIds = new HashSet<>();

    private final Object historyLock = new Object();
    private final HashMap<Loadable, History> historyByLoadable = new HashMap<>();

    public DatabaseManager(Context context) {
        helper = new DatabaseHelper(context);
        initialize();
    }

    /**
     * Save a reply to the savedreply table.
     * Threadsafe.
     *
     * @param saved the {@link SavedReply} to save
     */
    public void saveReply(SavedReply saved) {
        try {
            helper.savedDao.create(saved);
        } catch (SQLException e) {
            Logger.e(TAG, "Error saving reply", e);
        }

        synchronized (savedRepliesLock) {
            savedReplies.add(saved);
            savedRepliesIds.add(saved.no);
        }
    }

    /**
     * Searches a saved reply. This is done through caching members, no database lookups.
     * Threadsafe.
     *
     * @param board board for the reply to search
     * @param no    no for the reply to search
     * @return A {@link SavedReply} that matches {@code board} and {@code no}, or {@code null}
     */
    public SavedReply getSavedReply(String board, int no) {
        synchronized (savedRepliesLock) {
            if (savedRepliesIds.contains(no)) {
                for (SavedReply r : savedReplies) {
                    if (r.no == no && r.board.equals(board)) {
                        return r;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Searches if a saved reply exists. This is done through caching members, no database lookups.
     * Threadsafe.
     *
     * @param board board for the reply to search
     * @param no    no for the reply to search
     * @return true if a {@link SavedReply} matched {@code board} and {@code no}, {@code false} otherwise
     */
    public boolean isSavedReply(String board, int no) {
        return getSavedReply(board, no) != null;
    }

    /**
     * Adds a {@link Pin} to the pin table.
     *
     * @param pin Pin to save
     */
    public void addPin(Pin pin) {
        try {
            helper.loadableDao.create(pin.loadable);
            helper.pinDao.create(pin);
        } catch (SQLException e) {
            Logger.e(TAG, "Error adding pin to db", e);
        }
    }

    /**
     * Deletes a {@link Pin} from the pin table.
     *
     * @param pin Pin to delete
     */
    public void removePin(Pin pin) {
        try {
            helper.pinDao.delete(pin);
            helper.loadableDao.delete(pin.loadable);
        } catch (SQLException e) {
            Logger.e(TAG, "Error removing pin from db", e);
        }
    }

    /**
     * Updates a {@link Pin} in the pin table.
     *
     * @param pin Pin to update
     */
    public void updatePin(Pin pin) {
        try {
            helper.pinDao.update(pin);
            helper.loadableDao.update(pin.loadable);
        } catch (SQLException e) {
            Logger.e(TAG, "Error updating pin in db", e);
        }
    }

    /**
     * Updates all {@link Pin}s in the list to the pin table.
     *
     * @param pins Pins to update
     */
    public void updatePins(final List<Pin> pins) {
        try {
            callInTransaction(helper.getConnectionSource(), new Callable<Void>() {
                @Override
                public Void call() throws SQLException {
                    for (Pin pin : pins) {
                        helper.pinDao.update(pin);
                    }

                    for (Pin pin : pins) {
                        helper.loadableDao.update(pin.loadable);
                    }

                    return null;
                }
            });
        } catch (Exception e) {
            Logger.e(TAG, "Error updating pins in db", e);
        }
    }

    /**
     * Get a list of {@link Pin}s from the pin table.
     *
     * @return List of Pins
     */
    public List<Pin> getPinned() {
        List<Pin> list = null;
        try {
            list = helper.pinDao.queryForAll();
            for (Pin p : list) {
                helper.loadableDao.refresh(p.loadable);
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting pins from db", e);
        }

        return list;
    }

    /**
     * Adds or updates a {@link History} to the history table.
     * Only updates the date if the history is already in the table.
     *
     * @param history History to save
     */
    public void addHistory(final History history) {
        backgroundExecutor.submit(new Runnable() {
            @Override
            public void run() {
                addHistoryInternal(history);
            }
        });
    }

    /**
     * Deletes a {@link History} from the history table.
     *
     * @param history History to delete
     */
    public void removeHistory(History history) {
        try {
            helper.historyDao.delete(history);
            helper.loadableDao.delete(history.loadable);
            historyByLoadable.remove(history.loadable);
        } catch (SQLException e) {
            Logger.e(TAG, "Error removing history from db", e);
        }
    }

    /**
     * Clears all history and the referenced loadables from the database.
     */
    public void clearHistory() {
        try {
            TransactionManager.callInTransaction(helper.getConnectionSource(), new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    List<History> historyList = getHistory();
                    for (History history : historyList) {
                        removeHistory(history);
                    }

                    return null;
                }
            });
        } catch (SQLException e) {
            Logger.e(TAG, "Error clearing history", e);
        }
    }

    /**
     * Get a list of {@link History} entries from the history table.
     *
     * @return List of History
     */
    public List<History> getHistory() {
        List<History> list = null;
        try {
            QueryBuilder<History, Integer> historyQuery = helper.historyDao.queryBuilder();
            list = historyQuery.orderBy("date", false).query();
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting history from db", e);
        }

        return list;
    }

    /**
     * Clears all stored saved replies
     */
    public void clearSavedReplyHistory() {
        try {
            TransactionManager.callInTransaction(helper.getConnectionSource(), new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    List<SavedReply> replyList = getSavedReplyList();
                    for (SavedReply savedReply : replyList) {
                        removeSavedReply(savedReply);
                    }

                    return null;
                }
            });
        } catch (SQLException e) {
            Logger.e(TAG, "Error clearing saved replies", e);
        }
    }

    /**
     * Deletes a {@link SavedReply} from the saved table.
     *
     * @param savedReply SavedReply to delete
     */
    public void removeSavedReply(SavedReply savedReply) {
        try {
            helper.savedDao.delete(savedReply);
            savedReplies.remove(savedReply);
            savedRepliesIds.remove(savedReply.no);
        } catch (SQLException e) {
            Logger.e(TAG, "Error removing saved reply from db", e);
        }
    }

    /**
     * Get a list of {@link SavedReply} entries from the saved table.
     *
     * @return List of SavedReply
     */
    public List<SavedReply> getSavedReplyList() {
        List<SavedReply> list = null;
        try {
            QueryBuilder<SavedReply, Integer> savedReplyQuery = helper.savedDao.queryBuilder();
            list = savedReplyQuery.query();
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting saved replies from db", e);
        }

        return list;
    }

    public void addOrUpdateFilter(Filter filter) {
        try {
            helper.filterDao.createOrUpdate(filter);
        } catch (SQLException e) {
            Logger.e(TAG, "Error adding filter to db", e);
        }
    }

    public void removeFilter(Filter filter) {
        try {
            helper.filterDao.delete(filter);
        } catch (SQLException e) {
            Logger.e(TAG, "Error removing filter from db", e);
        }
    }

    public List<Filter> getFilters() {
        List<Filter> list = null;
        try {
            list = helper.filterDao.queryForAll();
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting filters from db", e);
        }

        return list;
    }

    /**
     * Create or updates these boards in the boards table.
     *
     * @param boards List of boards to create or update
     */
    public void setBoards(final List<Board> boards) {
        try {
            callInTransaction(helper.getConnectionSource(), new Callable<Void>() {
                @Override
                public Void call() throws SQLException {
                    for (Board b : boards) {
                        helper.boardsDao.createOrUpdate(b);
                    }

                    return null;
                }
            });
        } catch (Exception e) {
            Logger.e(TAG, "Error setting boards in db", e);
        }
    }

    /**
     * Get all boards from the boards table.
     *
     * @return all boards from the boards table
     */
    public List<Board> getBoards() {
        List<Board> boards = null;
        try {
            boards = helper.boardsDao.queryForAll();
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting boards from db", e);
        }

        return boards;
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
                if (hide.no == post.no && hide.board.equals(post.board)) {
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

    /**
     * Reset all tables in the database. Used for the developer screen.
     */
    public void reset() {
        helper.reset();
        initialize();
    }

    private void initialize() {
        loadSavedReplies();
        loadThreadHides();
        loadHistory();
    }

    /**
     * Threadsafe.
     */
    private void loadSavedReplies() {
        try {
            trimTable(helper.savedDao, "savedreply", SAVED_REPLY_TRIM_TRIGGER, SAVED_REPLY_TRIM_COUNT);

            synchronized (savedRepliesLock) {
                savedReplies.clear();
                savedReplies.addAll(helper.savedDao.queryForAll());
                savedRepliesIds.clear();
                for (SavedReply reply : savedReplies) {
                    savedRepliesIds.add(reply.no);
                }
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error loading saved replies", e);
        }
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

    private void loadHistory() {
        synchronized (historyLock) {
            try {
                trimTable(helper.historyDao, "history", HISTORY_TRIM_TRIGGER, HISTORY_TRIM_COUNT);

                historyByLoadable.clear();
                List<History> historyList = helper.historyDao.queryForAll();
                for (History history : historyList) {
                    historyByLoadable.put(history.loadable, history);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void addHistoryInternal(final History history) {
        try {
            TransactionManager.callInTransaction(helper.getConnectionSource(), new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    synchronized (historyLock) {
                        History existingHistory = historyByLoadable.get(history.loadable);
                        if (existingHistory != null) {
                            existingHistory.date = System.currentTimeMillis();
                            helper.historyDao.update(existingHistory);
                        } else {
                            history.date = System.currentTimeMillis();
                            helper.loadableDao.create(history.loadable);
                            helper.historyDao.create(history);
                            historyByLoadable.put(history.loadable, history);
                        }
                    }

                    return null;
                }
            });
        } catch (SQLException e) {
            Logger.e(TAG, "Error adding history", e);
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
    private void trimTable(Dao dao, String table, long trigger, long trim) {
        try {
            long count = dao.countOf();
            if (count > trigger) {
                dao.executeRaw("DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table + " ORDER BY id ASC LIMIT ?)", String.valueOf(trim));
                Logger.i(TAG, "Trimmed " + table + " from " + count + " to " + dao.countOf() + " rows");
            }
        } catch (SQLException e) {
            Logger.e(TAG, "Error trimming table " + table, e);
        }
    }
}
