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

    public List<Post> getNewQuotes() {
        if (posts.size() == 0) {
            return posts;
        } else {
            return posts.subList(Math.max(0, posts.size() - pin.getNewQuoteCount()), posts.size());
        }
    }

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

        if (pin.watchLastCount <= 0)
            pin.watchLastCount = pin.watchNewCount;

        if (pin.quoteLastCount <= 0)
            pin.quoteLastCount = pin.quoteNewCount;

        pin.watchNewCount = result.size();

        // Get list of saved posts
        List<Post> savedPosts = new ArrayList<Post>();
        for (Post saved : result) {
            if (saved.isSavedReply) {
                savedPosts.add(saved);
            }
        }

        int lastQuoteCount = pin.quoteNewCount;

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

        if (pin.quoteNewCount > lastQuoteCount) {
            wereNewQuotes = true;
        }

        WatchService.onPinWatcherResult();
    }
}
