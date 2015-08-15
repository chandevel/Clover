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
package org.floens.chan.chan;

import android.text.TextUtils;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.floens.chan.Chan;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.net.ChanReaderRequest;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChanLoader implements Response.ErrorListener, Response.Listener<ChanReaderRequest.ChanReaderResponse> {
    private static final String TAG = "ChanLoader";
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private static final int[] watchTimeouts = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    private final List<ChanLoaderCallback> listeners = new ArrayList<>();
    private final Loadable loadable;
    private final RequestQueue volleyRequestQueue;
    private ChanThread thread;

    private ChanReaderRequest request;

    private int currentTimeout = -1;
    private int lastPostCount;
    private long lastLoadTime;
    private ScheduledFuture<?> pendingFuture;

    public ChanLoader(Loadable loadable) {
        this.loadable = loadable;

        if (loadable.mode == Loadable.Mode.BOARD) {
            loadable.mode = Loadable.Mode.CATALOG;
        }

        volleyRequestQueue = Chan.getVolleyRequestQueue();
    }

    /**
     * Add a LoaderListener
     *
     * @param listener the listener to add
     */
    public void addListener(ChanLoaderCallback listener) {
        listeners.add(listener);
    }

    /**
     * Remove a LoaderListener
     *
     * @param listener the listener to remove
     * @return true if there are no more listeners, false otherwise
     */
    public boolean removeListener(ChanLoaderCallback listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            clearTimer();
            if (request != null) {
                request.cancel();
                request = null;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Request data for the first time.
     */
    public void requestData() {
        clearTimer();

        if (request != null) {
            request.cancel();
            // request = null;
        }

        if (loadable.isCatalogMode()) {
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
        }

        currentTimeout = -1;
        thread = null;

        request = getData();
    }

    /**
     * Request more data
     */
    public void requestMoreData() {
        clearPendingRunnable();

        if (loadable.isThreadMode() && request == null) {
            request = getData();
        }
    }

    /**
     * Request more data if the time left is below 0 If auto load more is
     * disabled, this needs to be called manually. Otherwise this is called
     * automatically when the timer hits 0.
     */
    public void loadMoreIfTime() {
        if (getTimeUntilLoadMore() < 0L) {
            requestMoreData();
        }
    }

    public void quickLoad() {
        if (thread == null) {
            throw new IllegalStateException("Cannot quick load without already loaded thread");
        }

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderData(thread);
        }

        requestMoreData();
    }

    /**
     * Request more data and reset the watch timer.
     */
    public void requestMoreDataAndResetTimer() {
        if (request == null) {
            clearTimer();
            requestMoreData();
        }
    }

    public boolean isLoading() {
        return request != null;
    }

    public Loadable getLoadable() {
        return loadable;
    }

    /**
     * Get the time in milliseconds until another loadMore is recommended
     */
    public long getTimeUntilLoadMore() {
        if (request != null) {
            return 0L;
        } else {
            long waitTime = watchTimeouts[Math.max(0, currentTimeout)] * 1000L;
            return lastLoadTime + waitTime - Time.get();
        }
    }

    public ChanThread getThread() {
        return thread;
    }

    @Override
    public void onResponse(ChanReaderRequest.ChanReaderResponse response) {
        request = null;

        if (response.posts.size() == 0) {
            onErrorResponse(new VolleyError("Post size is 0"));
            return;
        }

        if (thread == null) {
            thread = new ChanThread(loadable, new ArrayList<Post>());
        }

        thread.posts.clear();
        thread.posts.addAll(response.posts);

        processResponse(response);

        if (TextUtils.isEmpty(loadable.title)) {
            loadable.title = PostHelper.getTitle(thread.op, loadable);
        }

        for (Post post : thread.posts) {
            post.title = loadable.title;
        }

        lastLoadTime = Time.get();

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderData(thread);
        }
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        request = null;

        Logger.i(TAG, "Loading error", error);

        clearTimer();

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderError(error);
        }
    }

    /**
     * Final processing af a response that needs to happen on the main thread.
     *
     * @param response Response to process
     */
    private void processResponse(ChanReaderRequest.ChanReaderResponse response) {
        if (loadable.isThreadMode() && thread.posts.size() > 0) {
            // Replace some op parameters to the real op (index 0).
            // This is done on the main thread to avoid race conditions.
            Post realOp = thread.posts.get(0);
            thread.op = realOp;
            Post fakeOp = response.op;
            if (fakeOp != null) {
                thread.closed = realOp.closed = fakeOp.closed;
                thread.archived = realOp.archived = fakeOp.archived;
                realOp.sticky = fakeOp.sticky;
                realOp.replies = fakeOp.replies;
                realOp.images = fakeOp.images;
                realOp.uniqueIps = fakeOp.uniqueIps;
            } else {
                Logger.e(TAG, "Thread has no op!");
            }
        }
    }

    public void setTimer() {
        clearPendingRunnable();

        int postCount = thread == null ? 0 : thread.posts.size();
        if (postCount > lastPostCount) {
            lastPostCount = postCount;
            currentTimeout = 0;
        } else {
            currentTimeout++;
            currentTimeout = Math.min(currentTimeout, watchTimeouts.length - 1);
        }

        int watchTimeout = watchTimeouts[currentTimeout];
        Logger.d(TAG, "Scheduled reload in " + watchTimeout + "s");

        pendingFuture = executor.schedule(new Runnable() {
            @Override
            public void run() {
                AndroidUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        pendingFuture = null;
                        requestMoreData();
                    }
                });
            }
        }, watchTimeout, TimeUnit.SECONDS);
    }

    public void clearTimer() {
        currentTimeout = -1;
        clearPendingRunnable();
    }

    private void clearPendingRunnable() {
        if (pendingFuture != null) {
            Logger.d(TAG, "Cleared timer");
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    private ChanReaderRequest getData() {
        Logger.d(TAG, "Requested " + loadable.board + ", " + loadable.no);

        List<Post> cached = thread == null ? new ArrayList<Post>() : thread.posts;
        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, cached, this, this);

        volleyRequestQueue.add(request);

        return request;
    }

    public interface ChanLoaderCallback {
        void onChanLoaderData(ChanThread result);

        void onChanLoaderError(VolleyError error);
    }
}
