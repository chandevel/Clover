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

import org.floens.chan.ChanApplication;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class PinWatcher implements Loader.LoaderListener {
    private static final String TAG = "PinWatcher";

    private final Pin pin;
    private Loader loader;

    private final List<Post> posts = new ArrayList<>();
    private boolean wereNewQuotes = false;
    private boolean wereNewPosts = false;

    public PinWatcher(Pin pin) {
        this.pin = pin;

        loader = LoaderPool.getInstance().obtain(pin.loadable, this);
    }

    public void destroy() {
        if (loader != null) {
            LoaderPool.getInstance().release(loader, this);
            loader = null;
        }
    }

    public void update() {
        if (!pin.isError) {
            loader.loadMoreIfTime();
        }
    }

    public void onViewed() {
        if (pin.watchNewCount >= 0) {
            pin.watchLastCount = pin.watchNewCount;
        }

        pin.quoteLastCount = pin.quoteNewCount;
        wereNewQuotes = false;
        wereNewPosts = false;
    }

    public List<Post> getNewPosts() {
        if (posts.size() == 0) {
            return posts;
        } else {
            return posts.subList(Math.max(0, posts.size() - pin.getNewPostsCount()), posts.size());
        }
    }

    /* Currently not used
    public List<Post> getNewQuotes() {
        if (posts.size() == 0) {
            return posts;
        } else {
            return posts.subList(Math.max(0, posts.size() - pin.getNewQuoteCount()), posts.size());
        }
    }*/

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

    public Post getLastSeenPost() {
        int i = posts.size() - pin.getNewPostsCount() - 1;
        if (i >= 0 && i < posts.size()) {
            return posts.get(i);
        } else {
            return null;
        }
    }

    @Override
    public void onError(VolleyError error) {
        Logger.e(TAG, "PinWatcher onError: ", error);
        pin.isError = true;

        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getWatchManager().onPinsChanged();
            }
        });
    }

    @Override
    public void onData(List<Post> result, boolean append) {
        pin.isError = false;

        posts.clear();
        posts.addAll(result);

        int lastQuoteNewCount = pin.quoteNewCount;
        int lastWatchNewCount = pin.watchNewCount;

        if (pin.watchLastCount < 0)
            pin.watchLastCount = result.size();

        pin.watchNewCount = result.size();

        // Get list of saved posts
        int total = 0;
        for (Post saved : result) {
            if (saved.isSavedReply) {
                total += saved.repliesFrom.size();
            }
        }

        pin.quoteNewCount = total;

        if (pin.quoteNewCount > lastQuoteNewCount) {
            wereNewQuotes = true;
        }

        if (pin.watchNewCount > lastWatchNewCount) {
            wereNewPosts = true;
        }

        if (Logger.debugEnabled()) {
            Logger.d(TAG, String.format("postlast=%d postnew=%d werenewposts=%b quotelast=%d quotenew=%d werenewquotes=%b",
                    pin.watchLastCount, pin.watchNewCount, wereNewPosts, pin.quoteLastCount, pin.quoteNewCount, wereNewQuotes));
        }

        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getWatchManager().onPinsChanged();
            }
        });
    }
}
