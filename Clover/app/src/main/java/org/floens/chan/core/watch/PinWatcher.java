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
package org.floens.chan.core.watch;

import com.android.volley.VolleyError;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanLoader;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.pool.LoaderPool;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class PinWatcher implements ChanLoader.ChanLoaderCallback {
    private static final String TAG = "PinWatcher";

    private final Pin pin;
    private ChanLoader chanLoader;

    private final List<Post> posts = new ArrayList<>();
    private final List<Post> quotes = new ArrayList<>();
    private boolean wereNewQuotes = false;
    private boolean wereNewPosts = false;

    public PinWatcher(Pin pin) {
        this.pin = pin;

        chanLoader = LoaderPool.getInstance().obtain(pin.loadable, this);
    }

    public void destroy() {
        if (chanLoader != null) {
            LoaderPool.getInstance().release(chanLoader, this);
            chanLoader = null;
        }
    }

    public boolean update() {
        if (!pin.isError) {
            chanLoader.loadMoreIfTime();
            return true;
        } else {
            return false;
        }
    }

    public void onViewed() {
        if (pin.watchNewCount >= 0) {
            pin.watchLastCount = pin.watchNewCount;
        }
        wereNewPosts = false;

        if (pin.quoteNewCount >= 0) {
            pin.quoteLastCount = pin.quoteNewCount;
        }
        wereNewQuotes = false;
    }

    public List<Post> getUnviewedPosts() {
        if (posts.size() == 0) {
            return posts;
        } else {
            return posts.subList(Math.max(0, posts.size() - pin.getNewPostCount()), posts.size());
        }
    }

    public List<Post> getUnviewedQuotes() {
        return quotes.subList(Math.max(0, quotes.size() - pin.getNewQuoteCount()), quotes.size());
    }

    public boolean getWereNewQuotes() {
        if (wereNewQuotes) {
            wereNewQuotes = false;
            return true;
        } else {
            return false;
        }
    }

    public boolean getWereNewPosts() {
        if (wereNewPosts) {
            wereNewPosts = false;
            return true;
        } else {
            return false;
        }
    }

    public long getTimeUntilNextLoad() {
        return chanLoader.getTimeUntilLoadMore();
    }

    public boolean isLoading() {
        return chanLoader.isLoading();
    }

    @Override
    public void onChanLoaderError(VolleyError error) {
        Logger.e(TAG, "PinWatcher onError");
        pin.isError = true;

        pin.watching = false;
        Chan.getWatchManager().pinWatcherUpdated(pin);
    }

    @Override
    public void onChanLoaderData(ChanThread thread) {
        pin.isError = false;

        if (pin.thumbnailUrl == null && thread.op != null && thread.op.hasImage) {
            pin.thumbnailUrl = thread.op.thumbnailUrl;
        }

        // Populate posts list
        posts.clear();
        posts.addAll(thread.posts);

        // Populate quotes list
        quotes.clear();

        // Get list of saved replies from this thread
        List<Post> savedReplies = new ArrayList<>();
        for (Post item : thread.posts) {
//            saved.title = pin.loadable.title;

            if (item.isSavedReply) {
                savedReplies.add(item);
            }
        }

        // Now get a list of posts that have a quote to a saved reply
        for (Post post : thread.posts) {
            for (Post saved : savedReplies) {
                if (post.repliesTo.contains(saved.no)) {
                    quotes.add(post);
                }
            }
        }

        boolean isFirstLoad = pin.watchNewCount < 0 || pin.quoteNewCount < 0;

        // If it was more than before processing
        int lastWatchNewCount = pin.watchNewCount;
        int lastQuoteNewCount = pin.quoteNewCount;

        if (isFirstLoad) {
            pin.watchLastCount = posts.size();
            pin.quoteLastCount = quotes.size();
        }

        pin.watchNewCount = posts.size();
        pin.quoteNewCount = quotes.size();

        if (!isFirstLoad) {
            // There were new posts after processing
            if (pin.watchNewCount > lastWatchNewCount) {
                wereNewPosts = true;
            }

            // There were new quotes after processing
            if (pin.quoteNewCount > lastQuoteNewCount) {
                wereNewQuotes = true;
            }
        }

        if (Logger.debugEnabled()) {
            Logger.d(TAG, String.format("postlast=%d postnew=%d werenewposts=%b quotelast=%d quotenew=%d werenewquotes=%b",
                    pin.watchLastCount, pin.watchNewCount, wereNewPosts, pin.quoteLastCount, pin.quoteNewCount, wereNewQuotes));
        }

        if (thread.archived || thread.closed) {
            pin.archived = true;
            pin.watching = false;
        }

        Chan.getWatchManager().pinWatcherUpdated(pin);
    }
}
