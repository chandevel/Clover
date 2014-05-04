/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.WatchService;
import org.floens.chan.utils.Logger;

import com.android.volley.VolleyError;

public class PinWatcher implements Loader.LoaderListener {
    private static final String TAG = "PinWatcher";

    private final Pin pin;
    private Loader loader;

    private final List<Post> posts = new ArrayList<Post>();
    private boolean wereNewQuotes = false;

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
        pin.watchLastCount = pin.watchNewCount;
        pin.quoteLastCount = pin.quoteNewCount;
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

        WatchService.onPinWatcherResult();
    }

    @Override
    public void onData(List<Post> result, boolean append) {
        pin.isError = false;

        posts.clear();
        posts.addAll(result);

        if (pin.watchLastCount < 0)
            pin.watchLastCount = pin.watchNewCount;

        pin.watchNewCount = result.size();

        // Get list of saved posts
        List<Post> savedPosts = new ArrayList<Post>();
        for (Post saved : result) {
            if (saved.isSavedReply) {
                savedPosts.add(saved);
            }
        }

        // If there are more replies than last time, let the notification make a sound
        int lastCounterForSoundNotification = pin.quoteNewCount;

        // Find posts quoting these saved posts
        pin.quoteNewCount = 0;
        for (Post resultPost : result) {
            // This post replies to me
            for (Post savedPost : savedPosts) {
                if (resultPost.repliesTo.contains(savedPost.no)) {
                    pin.quoteNewCount++;
                }
            }
        }

        if (pin.quoteNewCount > lastCounterForSoundNotification) {
            wereNewQuotes = true;
        }

        WatchService.onPinWatcherResult();
    }
}
