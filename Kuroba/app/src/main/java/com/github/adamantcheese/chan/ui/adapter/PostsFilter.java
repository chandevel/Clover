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

import static com.github.adamantcheese.chan.Chan.instance;

import android.text.TextUtils;

import androidx.annotation.StringRes;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.primitives.OptionSettingItem;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.*;

public class PostsFilter {

    public enum PostsOrder
            implements OptionSettingItem {
        BUMP(R.string.order_bump, (lhs, rhs) -> 0),
        REPLY(R.string.order_reply, (lhs, rhs) -> rhs.replies - lhs.replies),
        IMAGE(R.string.order_image, (lhs, rhs) -> rhs.imagesCount - lhs.imagesCount),
        NEWEST(R.string.order_newest, (lhs, rhs) -> (int) (rhs.time - lhs.time)),
        OLDEST(R.string.order_oldest, (lhs, rhs) -> (int) (lhs.time - rhs.time)),
        MODIFIED(R.string.order_modified, (lhs, rhs) -> (int) (rhs.lastModified - lhs.lastModified)),
        ACTIVITY(R.string.order_activity, (lhs, rhs) -> {
            long currentTimeSeconds = System.currentTimeMillis() / 1000L;

            //we can't divide by zero, but we can divide by the smallest thing that's closest to 0 instead
            long score1 =
                    (long) ((currentTimeSeconds - lhs.time) / (lhs.replies != 0 ? lhs.replies : Float.MIN_NORMAL));
            long score2 =
                    (long) ((currentTimeSeconds - rhs.time) / (rhs.replies != 0 ? rhs.replies : Float.MIN_NORMAL));

            return Long.compare(score1, score2);
        });

        public final Comparator<Post> postComparator;
        @StringRes
        public final int displayIdRes;

        PostsOrder(@StringRes int displayId, Comparator<Post> c) {
            postComparator = c;
            displayIdRes = displayId;
        }

        @Override
        public String getKey() {
            return name().toLowerCase();
        }
    }

    public final PostsOrder postsOrder;
    public final String query;

    public PostsFilter(PostsOrder postsOrder, String query) {
        this.postsOrder = postsOrder;
        this.query = query;
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
        Collections.sort(posts, postsOrder.postComparator);

        // Process search
        if (!TextUtils.isEmpty(query)) {
            boolean add;
            Iterator<Post> i = posts.iterator();
            while (i.hasNext()) {
                Post post = i.next();
                add = false;
                if (StringUtils.containsIgnoreCase(post.comment, query)) {
                    add = true;
                } else if (StringUtils.containsIgnoreCase(post.subject, query)) {
                    add = true;
                } else if (StringUtils.containsIgnoreCase(post.name, query)) {
                    add = true;
                } else if (!post.images.isEmpty()) {
                    for (PostImage image : post.images) {
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
        if (ChanSettings.removeWatchedFromCatalog.get() && thread.loadable.isCatalogMode()) {
            Iterator<Post> i = posts.iterator();
            List<Pin> pins = new ArrayList<>(instance(WatchManager.class).getAllPins());
            while (i.hasNext()) {
                Post item = i.next();
                for (Pin pin : pins) {
                    if (pin.loadable.equals(Loadable.forThread(thread.loadable.board, item.no, "", false))) {
                        i.remove();
                    }
                }
            }
        }

        // Process hidden by filter and post/thread hiding
        return instance(DatabaseHideManager.class).filterHiddenPosts(posts,
                thread.loadable.siteId,
                thread.loadable.boardCode
        );
    }
}
