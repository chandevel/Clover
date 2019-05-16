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
package com.github.adamantcheese.chan.core.site.loader;

import android.text.TextUtils;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderRequest;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * A ChanThreadLoader is the loader for Loadables.
 * <p>Obtain ChanLoaders with {@link com.github.adamantcheese.chan.core.pool.ChanLoaderFactory}.
 * <p>ChanLoaders can load boards and threads, and return {@link ChanThread} objects on success, through
 * {@link ChanLoaderCallback}.
 * <p>For threads timers can be started with {@link #setTimer()} to do a request later.
 */
public class ChanThreadLoader implements Response.ErrorListener, Response.Listener<ChanLoaderResponse> {
    private static final String TAG = "ChanThreadLoader";
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private static final int[] WATCH_TIMEOUTS = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    @Inject
    RequestQueue volleyRequestQueue;

    private final List<ChanLoaderCallback> listeners = new ArrayList<>();
    private final Loadable loadable;
    private ChanThread thread;

    private ChanLoaderRequest request;

    private int currentTimeout = 0;
    private int lastPostCount;
    private long lastLoadTime;
    private ScheduledFuture<?> pendingFuture;

    /**
     * <b>Do not call this constructor yourself, obtain ChanLoaders through {@link com.github.adamantcheese.chan.core.pool.ChanLoaderFactory}</b>
     */
    public ChanThreadLoader(Loadable loadable) {
        this.loadable = loadable;

        inject(this);

        if (loadable.mode == Loadable.Mode.BOARD) {
            loadable.mode = Loadable.Mode.CATALOG;
        }
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
                request.getVolleyRequest().cancel();
                request = null;
            }
            return true;
        } else {
            return false;
        }
    }

    public ChanThread getThread() {
        return thread;
    }

    /**
     * Request data for the first time.
     */
    public void requestData() {
        clearTimer();

        if (request != null) {
            request.getVolleyRequest().cancel();
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
     * Request more data. This only works for thread loaders.<br>
     * This clears any pending pending timers, created with {@link #setTimer()}.
     *
     * @return {@code true} if a new request was started, {@code false} otherwise.
     */
    public boolean requestMoreData() {
        clearPendingRunnable();

        if (loadable.isThreadMode() && request == null) {
            request = getData();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Request more data if {@link #getTimeUntilLoadMore()} is negative.
     */
    public boolean loadMoreIfTime() {
        return getTimeUntilLoadMore() < 0L && requestMoreData();
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

    public Loadable getLoadable() {
        return loadable;
    }

    public void setTimer() {
        clearPendingRunnable();

        int watchTimeout = WATCH_TIMEOUTS[currentTimeout];
        Logger.d(TAG, "Scheduled reload in " + watchTimeout + "s");

        pendingFuture = executor.schedule(() -> AndroidUtils.runOnUiThread(() -> {
            pendingFuture = null;
            requestMoreData();
        }), watchTimeout, TimeUnit.SECONDS);
    }

    public void clearTimer() {
        currentTimeout = 0;
        clearPendingRunnable();
    }

    /**
     * Get the time in milliseconds until another loadMore is recommended
     */
    public long getTimeUntilLoadMore() {
        if (request != null) {
            return 0L;
        } else {
            long waitTime = WATCH_TIMEOUTS[Math.max(0, currentTimeout)] * 1000L;
            return lastLoadTime + waitTime - System.currentTimeMillis();
        }
    }

    private ChanLoaderRequest getData() {
        Logger.d(TAG, "Requested " + loadable.boardCode + ", " + loadable.no);

        List<Post> cached = thread == null ? new ArrayList<>() : thread.posts;

        ChanReader chanReader = loadable.getSite().chanReader();

        ChanLoaderRequestParams requestParams = new ChanLoaderRequestParams(loadable, chanReader, cached, this, this);
        ChanReaderRequest readerRequest = new ChanReaderRequest(requestParams);
        request = new ChanLoaderRequest(readerRequest);

        volleyRequestQueue.add(request.getVolleyRequest());

        return request;
    }

    @Override
    public void onResponse(ChanLoaderResponse response) {
        request = null;

        if (response.posts.isEmpty()) {
            onErrorResponse(new VolleyError("Post size is 0"));
            return;
        }

        if (thread == null) {
            thread = new ChanThread(loadable, new ArrayList<>());
        }

        thread.posts.clear();
        thread.posts.addAll(response.posts);

        processResponse(response);

        if (TextUtils.isEmpty(loadable.title)) {
            loadable.setTitle(PostHelper.getTitle(thread.op, loadable));
        }

        for (Post post : thread.posts) {
            post.setTitle(loadable.title);
        }

        lastLoadTime = System.currentTimeMillis();

        int postCount = thread.posts.size();
        if (postCount > lastPostCount) {
            lastPostCount = postCount;
            currentTimeout = 0;
        } else {
            currentTimeout = Math.min(currentTimeout + 1, WATCH_TIMEOUTS.length - 1);
        }

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderData(thread);
        }
    }

    /**
     * Final processing af a response that needs to happen on the main thread.
     *
     * @param response Response to process
     */
    private void processResponse(ChanLoaderResponse response) {
        if (loadable.isThreadMode() && thread.posts.size() > 0) {
            // Replace some op parameters to the real op (index 0).
            // This is done on the main thread to avoid race conditions.
            Post realOp = thread.posts.get(0);
            thread.op = realOp;
            Post.Builder fakeOp = response.op;
            if (fakeOp != null) {
                realOp.setClosed(fakeOp.closed);
                thread.closed = realOp.isClosed();
                realOp.setArchived(fakeOp.archived);
                thread.archived = realOp.isArchived();
                realOp.setSticky(fakeOp.sticky);
                realOp.setReplies(fakeOp.replies);
                realOp.setImagesCount(fakeOp.imagesCount);
                realOp.setUniqueIps(fakeOp.uniqueIps);
                realOp.setLastModified(fakeOp.lastModified);
            } else {
                Logger.e(TAG, "Thread has no op!");
            }
        }
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        request = null;

        Logger.i(TAG, "Loading error", error);

        clearTimer();

        ChanLoaderException loaderException = new ChanLoaderException(error);

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderError(loaderException);
        }
    }

    private void clearPendingRunnable() {
        if (pendingFuture != null) {
            Logger.d(TAG, "Cleared timer");
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    public interface ChanLoaderCallback {
        void onChanLoaderData(ChanThread result);

        void onChanLoaderError(ChanLoaderException error);
    }

    public class ChanLoaderException extends Exception {
        private VolleyError volleyError;

        public ChanLoaderException(VolleyError volleyError) {
            this.volleyError = volleyError;
        }

        public boolean isNotFound() {
            return volleyError instanceof ServerError && isServerErrorNotFound((ServerError) volleyError);
        }

        public int getErrorMessage() {
            int errorMessage;
            if (volleyError.getCause() instanceof SSLException) {
                errorMessage = R.string.thread_load_failed_ssl;
            } else if (volleyError instanceof NetworkError ||
                    volleyError instanceof TimeoutError ||
                    volleyError instanceof ParseError ||
                    volleyError instanceof AuthFailureError) {
                errorMessage = R.string.thread_load_failed_network;
            } else if (volleyError instanceof ServerError) {
                if (isServerErrorNotFound((ServerError) volleyError)) {
                    errorMessage = R.string.thread_load_failed_not_found;
                } else {
                    errorMessage = R.string.thread_load_failed_server;
                }
            } else {
                errorMessage = R.string.thread_load_failed_parsing;
            }
            return errorMessage;
        }

        private boolean isServerErrorNotFound(ServerError serverError) {
            return serverError.networkResponse != null && serverError.networkResponse.statusCode == 404;
        }
    }
}
