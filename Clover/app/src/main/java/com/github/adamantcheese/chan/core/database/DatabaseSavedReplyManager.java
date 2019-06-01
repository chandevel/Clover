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

import android.support.annotation.AnyThread;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.table.TableUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * Saved replies are posts-password combinations used to track what posts are posted by the app,
 * and used to delete posts.
 */
public class DatabaseSavedReplyManager {
    private static final String TAG = "DatabaseSavedReplyManager";

    private static final long SAVED_REPLY_TRIM_TRIGGER = 250;
    private static final long SAVED_REPLY_TRIM_COUNT = 50;

    @Inject
    DatabaseHelper helper;

    private final Map<Integer, List<SavedReply>> savedRepliesByNo = new HashMap<>();

    public DatabaseSavedReplyManager() {
        inject(this);
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
        synchronized (savedRepliesByNo) {
            if (savedRepliesByNo.containsKey(postNo)) {
                List<SavedReply> items = savedRepliesByNo.get(postNo);
                for (int i = 0; i < items.size(); i++) {
                    SavedReply item = items.get(i);
                    if (item.board.equals(board.code) && item.siteId == board.site.id()) {
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
        return () -> {
            Chan.injector().provider(DatabaseManager.class).get().trimTable(helper.savedDao, "savedreply",
                    SAVED_REPLY_TRIM_TRIGGER, SAVED_REPLY_TRIM_COUNT);

            final List<SavedReply> all = helper.savedDao.queryForAll();

            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
                for (int i = 0; i < all.size(); i++) {
                    SavedReply savedReply = all.get(i);

                    savedReply.site = SiteRepository.forId(savedReply.siteId);

                    List<SavedReply> list = savedRepliesByNo.get(savedReply.no);
                    if (list == null) {
                        list = new ArrayList<>(1);
                        savedRepliesByNo.put(savedReply.no, list);
                    }

                    list.add(savedReply);
                }
            }
            return null;
        };
    }

    public Callable<Void> clearSavedReplies() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), SavedReply.class);
            synchronized (savedRepliesByNo) {
                savedRepliesByNo.clear();
            }
            return null;
        };
    }

    public Callable<SavedReply> saveReply(final SavedReply savedReply) {
        return () -> {
            helper.savedDao.create(savedReply);
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

    public Callable<SavedReply> findSavedReply(final Board board, final int no) {
        return () -> {
            QueryBuilder<SavedReply, Integer> builder = helper.savedDao.queryBuilder();
            List<SavedReply> query = builder.where()
                    .eq("site", board.site.id())
                    .and().eq("board", board.code)
                    .and().eq("no", no).query();
            return query.isEmpty() ? null : query.get(0);
        };
    }

    public Callable<Void> deleteSavedReplies(Site site) {
        return () -> {
            DeleteBuilder<SavedReply, Integer> builder = helper.savedDao.deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
