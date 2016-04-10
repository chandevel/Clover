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

import org.floens.chan.core.model.SavedReply;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseSavedReplyManager {
    private static final String TAG = "DatabaseSavedReplyManager";

    private static final long SAVED_REPLY_TRIM_TRIGGER = 250;
    private static final long SAVED_REPLY_TRIM_COUNT = 50;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    private final Map<Integer, List<SavedReply>> savedRepliesByNo = new HashMap<>();

    public DatabaseSavedReplyManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    // optimized and threadsafe
    public boolean isSaved(String board, int no) {
        synchronized (savedRepliesByNo) {
            if (savedRepliesByNo.containsKey(no)) {
                List<SavedReply> items = savedRepliesByNo.get(no);
                for (int i = 0; i < items.size(); i++) {
                    SavedReply item = items.get(i);
                    if (item.board.equals(board)) {
                        return true;
                    }
                }
                return false;
            } else {
                return false;
            }
        }
    }

    public Callable<Void> load() {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                databaseManager.trimTable(helper.savedDao, "savedreply", SAVED_REPLY_TRIM_TRIGGER, SAVED_REPLY_TRIM_COUNT);

                final List<SavedReply> all = helper.savedDao.queryForAll();

                synchronized (savedRepliesByNo) {
                    savedRepliesByNo.clear();
                    for (int i = 0; i < all.size(); i++) {
                        SavedReply savedReply = all.get(i);
                        if (savedRepliesByNo.containsKey(savedReply.no)) {
                            savedRepliesByNo.get(savedReply.no).add(savedReply);
                        } else {
                            List<SavedReply> items = new ArrayList<>();
                            items.add(savedReply);
                            savedRepliesByNo.put(savedReply.no, items);
                        }
                    }
                }
                return null;
            }
        };
    }

    public Callable<Void> clearSavedReplies() {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long start = Time.startTiming();
                TableUtils.clearTable(helper.getConnectionSource(), SavedReply.class);
                synchronized (savedRepliesByNo) {
                    savedRepliesByNo.clear();
                }
                Time.endTiming("Clear saved replies", start);

                return null;
            }
        };
    }

    public Callable<SavedReply> saveReply(final SavedReply savedReply) {
        return new Callable<SavedReply>() {
            @Override
            public SavedReply call() throws Exception {
                helper.savedDao.create(savedReply);
                synchronized (savedRepliesByNo) {
                    if (savedRepliesByNo.containsKey(savedReply.no)) {
                        savedRepliesByNo.get(savedReply.no).add(savedReply);
                    } else {
                        List<SavedReply> items = new ArrayList<>();
                        items.add(savedReply);
                        savedRepliesByNo.put(savedReply.no, items);
                    }
                }
                return savedReply;
            }
        };
    }

    public Callable<SavedReply> findSavedReply(final String board, final int no) {
        return new Callable<SavedReply>() {
            @Override
            public SavedReply call() throws Exception {
                QueryBuilder<SavedReply, Integer> builder = helper.savedDao.queryBuilder();
                List<SavedReply> query = builder.where().eq("board", board).and().eq("no", no).query();
                return query.isEmpty() ? null : query.get(0);
            }
        };
    }
}
