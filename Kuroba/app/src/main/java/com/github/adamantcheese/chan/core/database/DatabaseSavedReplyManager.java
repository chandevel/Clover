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

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * Saved replies are posts-password combinations used to track what posts are posted by the app,
 * and used to delete posts.
 */
public class DatabaseSavedReplyManager {
    private static final long TRIM_TRIGGER = 250;
    private static final long TRIM_COUNT = 50;

    DatabaseHelper helper;

    // map of post number to saved replies
    private final Map<Integer, List<SavedReply>> savedRepliesByNo = new HashMap<>();

    public DatabaseSavedReplyManager(DatabaseHelper helper) {
        this.helper = helper;
        DatabaseUtils.runTask(DatabaseUtils.trimTable(helper.getSavedReplyDao(), TRIM_TRIGGER, TRIM_COUNT));
        DatabaseUtils.runTask(() -> {
            final List<SavedReply> all = helper.getSavedReplyDao().queryForAll();

            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
                for (SavedReply savedReply : all) {
                    List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        savedRepliesByNo.put(savedReply.no, list);
                    }

                    list.add(savedReply);
                }
            }
            return null;
        });
    }

    /**
     * Check if the given board-no combination is in the database.<br>
     * This is unlike other methods in that it immediately returns the result instead of
     * a Callable. This method is thread-safe and optimized.
     *
     * @param board  board of the post
     * @param postNo post number
     * @return {@code true} if the post is in the saved reply database, {@code false} otherwise.
     */
    @AnyThread
    public boolean isSaved(Board board, int postNo) {
        return getSavedReply(board, postNo) != null;
    }

    public Callable<SavedReply> saveReply(SavedReply savedReply) {
        return () -> {
            helper.getSavedReplyDao().create(savedReply);
            synchronized (savedRepliesByNo) {
                List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                if (list == null) {
                    list = new ArrayList<>(1);
                    savedRepliesByNo.put(savedReply.no, list);
                }

                list.add(savedReply);
            }
            return savedReply;
        };
    }

    public Callable<SavedReply> unsaveReply(SavedReply savedReply) {
        return () -> {
            helper.getSavedReplyDao().delete(savedReply);
            synchronized (savedRepliesByNo) {
                List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                if (list != null) {
                    list.remove(savedReply);
                    if (list.isEmpty()) {
                        savedRepliesByNo.remove(savedReply.no);
                    }
                }
            }
            return savedReply;
        };
    }

    public SavedReply getSavedReply(Board board, int postNo) {
        synchronized (savedRepliesByNo) {
            if (savedRepliesByNo.containsKey(postNo)) {
                List<SavedReply> items = savedRepliesByNo.get(postNo);
                for (SavedReply item : items) {
                    if (item.board.equals(board.code) && item.siteId == board.siteId) {
                        return item;
                    }
                }
            }
            return null;
        }
    }

    public Callable<Void> deleteSavedReplies(Site site) {
        return () -> {
            DeleteBuilder<SavedReply, Integer> builder = helper.getSavedReplyDao().deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
