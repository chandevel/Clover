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
package com.github.adamantcheese.chan.ui.adapter;

import android.text.TextUtils;

import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class PostsFilter {
    private static final Comparator<Post> IMAGE_COMPARATOR = (lhs, rhs) -> rhs.getImagesCount() - lhs.getImagesCount();

    private static final Comparator<Post> REPLY_COMPARATOR = (lhs, rhs) -> rhs.getReplies() - lhs.getReplies();

    private static final Comparator<Post> NEWEST_COMPARATOR = (lhs, rhs) -> (int) (rhs.time - lhs.time);

    private static final Comparator<Post> OLDEST_COMPARATOR = (lhs, rhs) -> (int) (lhs.time - rhs.time);

    private static final Comparator<Post> MODIFIED_COMPARATOR =
            (lhs, rhs) -> (int) (rhs.getLastModified() - lhs.getLastModified());

    private static final Comparator<Post> THREAD_ACTIVITY_COMPARATOR = (lhs, rhs) -> {
        long currentTimeSeconds = System.currentTimeMillis() / 1000L;

        //we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
        long score1 = (long) ((currentTimeSeconds - lhs.time) / (lhs.getReplies() != 0
                ? lhs.getReplies()
                : Float.MIN_NORMAL));
        long score2 = (long) ((currentTimeSeconds - rhs.time) / (rhs.getReplies() != 0
                ? rhs.getReplies()
                : Float.MIN_NORMAL));

        return Long.compare(score1, score2);
    };

    private Order order;
    private String query;
    private DatabaseHideManager databaseHideManager;

    public PostsFilter(Order order, String query, DatabaseHideManager databaseHideManager) {
        this.order = order;
        this.query = query;
        this.databaseHideManager = databaseHideManager;
    }

    /**
     * Creates a copy of {@code original} and applies any sorting or filtering to it.
     *
     * @param original List of {@link Post}s to filter.
     * @param siteId   to get rid of collisions when figuring out if a post is hidden.
     * @param board    to get rid of collisions when figuring out if a post is hidden.
     * @return a new filtered List
     */
    public List<Post> apply(List<Post> original, int siteId, String board) {
        List<Post> posts = new ArrayList<>(original);

        // Process order
        if (order != PostsFilter.Order.BUMP) {
            switch (order) {
                case IMAGE:
                    Collections.sort(posts, IMAGE_COMPARATOR);
                    break;
                case REPLY:
                    Collections.sort(posts, REPLY_COMPARATOR);
                    break;
                case NEWEST:
                    Collections.sort(posts, NEWEST_COMPARATOR);
                    break;
                case OLDEST:
                    Collections.sort(posts, OLDEST_COMPARATOR);
                    break;
                case MODIFIED:
                    Collections.sort(posts, MODIFIED_COMPARATOR);
                    break;
                case ACTIVITY:
                    Collections.sort(posts, THREAD_ACTIVITY_COMPARATOR);
                    break;
            }
        }

        // Process search
        if (!TextUtils.isEmpty(query)) {
            String lowerQuery = query.toLowerCase(Locale.ENGLISH);

            boolean add;
            Iterator<Post> i = posts.iterator();
            while (i.hasNext()) {
                Post item = i.next();
                add = false;
                if (item.comment.toString().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                    add = true;
                } else if (item.subject.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                    add = true;
                } else if (item.name.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                    add = true;
                } else if (!item.images.isEmpty()) {
                    for (PostImage image : item.images) {
                        if (image.filename != null && image.filename.toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                            add = true;
                        }
                    }
                }
                if (!add) {
                    i.remove();
                }
            }
        }

        // Process hidden by filter and post/thread hiding
        return databaseHideManager.filterHiddenPosts(posts, siteId, board);
    }

    public enum Order {
        BUMP,
        REPLY,
        IMAGE,
        NEWEST,
        OLDEST,
        MODIFIED,
        ACTIVITY;

        public static Order find(String name) {
            for (Order mode : Order.values()) {
                if (mode.name().toLowerCase().equals(name)) {
                    return mode;
                }
            }
            return null;
        }

        public static boolean isNotBumpOrder(String orderString) {
            Order o = find(orderString);
            return !BUMP.equals(o);
        }
    }
}
