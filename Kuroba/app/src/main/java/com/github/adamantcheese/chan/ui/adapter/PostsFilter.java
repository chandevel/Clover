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
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.github.adamantcheese.chan.Chan.instance;

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

    private final Order order;
    private final String query;

    public PostsFilter(Order order, String query) {
        this.order = order;
        this.query = query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PostsFilter that = (PostsFilter) o;
        return order == that.order && Objects.equals(query, that.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(order, query);
    }

    /**
     * Creates a copy of {@code original} and applies any sorting or filtering to it.
     *
     * @param thread The thread to filter
     * @return a new filtered List
     */
    public List<Post> apply(ChanThread thread) {
        List<Post> posts = new ArrayList<>(thread.getPosts());

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
            boolean add;
            Iterator<Post> i = posts.iterator();
            while (i.hasNext()) {
                Post item = i.next();
                add = false;
                if (StringUtils.containsIgnoreCase(item.comment, query)) {
                    add = true;
                } else if (StringUtils.containsIgnoreCase(item.subject, query)) {
                    add = true;
                } else if (StringUtils.containsIgnoreCase(item.name, query)) {
                    add = true;
                } else if (!item.images.isEmpty()) {
                    for (PostImage image : item.images) {
                        if (StringUtils.containsIgnoreCase(image.filename, query)) {
                            add = true;
                        }
                    }
                }
                if (!add) {
                    i.remove();
                }
            }
        }

        //Filter out any bookmarked threads from the catalog
        if (ChanSettings.removeWatchedFromCatalog.get() && thread.getLoadable().isCatalogMode()) {
            Iterator<Post> i = posts.iterator();
            List<Pin> pins = new ArrayList<>(instance(WatchManager.class).getAllPins());
            while (i.hasNext()) {
                Post item = i.next();
                for (Pin pin : pins) {
                    if (pin.loadable.equals(Loadable.forThread(thread.getLoadable().board, item.no, "", false))) {
                        i.remove();
                    }
                }
            }
        }

        // Process hidden by filter and post/thread hiding
        return instance(DatabaseHideManager.class).filterHiddenPosts(posts,
                thread.getLoadable().siteId,
                thread.getLoadable().boardCode
        );
    }

    public String getQuery() {
        return query;
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
