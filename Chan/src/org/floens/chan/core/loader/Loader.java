package org.floens.chan.core.loader;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.net.ChanReaderRequest;
import org.floens.chan.utils.Logger;

import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;

import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

public class Loader {
    private static final String TAG = "Loader";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    private static final int[] watchTimeouts = { 10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600 };

    private final List<LoaderListener> listeners = new ArrayList<LoaderListener>();
    private final Loadable loadable;
    private final SparseArray<Post> postsById = new SparseArray<Post>();
    private final List<Post> cachedPosts = new ArrayList<Post>();

    private boolean destroyed = false;
    private boolean autoReload = false;
    private ChanReaderRequest request;

    private int currentTimeout;
    private int lastPostCount;
    private long lastLoadTime;
    private Runnable pendingRunnable;

    public Loader(Loadable loadable) {
        this.loadable = loadable;
    }

    /**
     * Add a LoaderListener
     *
     * @param l
     *            the listener to add
     */
    public void addListener(LoaderListener l) {
        listeners.add(l);
    }

    /**
     * Remove a LoaderListener
     *
     * @param l
     *            the listener to remove
     * @return true if there are no more listeners, false otherwise
     */
    public boolean removeListener(LoaderListener l) {
        listeners.remove(l);
        if (listeners.size() == 0) {
            clearTimer();
            destroyed = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Enable this if requestMoreData should me called automatically when the
     * timer hits 0
     */
    public void setAutoLoadMore(boolean autoReload) {
        if (this.autoReload != autoReload) {
            Logger.test("Setting autoreload to " + autoReload);
            this.autoReload = autoReload;

            if (!autoReload) {
                clearTimer();
            }
        }
    }

    /**
     * Request more data if the time left is below 0
     * If auto load more is disabled, this needs to be called manually.
     * Otherwise this is called automatically when the timer hits 0.
     */
    public void tryLoadMoreIfTime() {
        if (getTimeUntilLoadMore() < 0L) {
            requestMoreData();
        }
    }

    /**
     * Request data for the first time.
     */
    public void requestData() {
        clearTimer();

        if (request != null) {
            request.cancel();
        }

        if (loadable.isBoardMode()) {
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
        }

        currentTimeout = 0;

        request = getData();
    }

    /**
     * Request more data
     */
    public void requestMoreData() {
        clearTimer();

        if (loadable.isBoardMode()) {
            loadable.no++;

            if (request != null) {
                request.cancel();
            }

            request = getData();
        } else if (loadable.isThreadMode()) {
            if (request != null) {
                return;
            }

            request = getData();
        }
    }

    /**
     * Request more data and reset the watch timer.
     */
    public void requestMoreDataAndResetTimer() {
        currentTimeout = 0;
        requestMoreData();
    }

    /**
     * @return Returns if this loader is currently loading
     */
    public boolean isLoading() {
        return request != null;
    }

    public Post findPostById(int id) {
        return postsById.get(id);
    }

    public Loadable getLoadable() {
        return loadable;
    }

    /**
     * Get the time in milliseconds until another loadMore is recommended
     * @return
     */
    public long getTimeUntilLoadMore() {
        if (request != null) {
            return 0L;
        } else {
            long waitTime = watchTimeouts[currentTimeout] * 1000L;
            return lastLoadTime + waitTime - System.currentTimeMillis();
        }
    }

    private void setTimer(int postCount) {
        if (pendingRunnable != null) {
            clearTimer();
        }

        if (pendingRunnable == null) {
            if (postCount > lastPostCount) {
                currentTimeout = 0;
            } else {
                currentTimeout++;
                if (currentTimeout >= watchTimeouts.length) {
                    currentTimeout = watchTimeouts.length - 1;
                }
            }

            if (!autoReload && currentTimeout < 4) {
                currentTimeout = 4; // At least 60 seconds in the background
            }

            lastPostCount = postCount;

            Logger.test("Current timeout: " + watchTimeouts[currentTimeout]);

            if (autoReload) {
                pendingRunnable = new Runnable() {
                    @Override
                    public void run() {
                        pendingRunnable = null;
                        tryLoadMoreIfTime();
                    };
                };

                Logger.test("Scheduled reload");
                handler.postDelayed(pendingRunnable, watchTimeouts[currentTimeout] * 1000L);

            }
        }
    }

    private void clearTimer() {
        if (pendingRunnable != null) {
            Logger.test("Removed reload");
            handler.removeCallbacks(pendingRunnable);
            pendingRunnable = null;
        }
    }

    private ChanReaderRequest getData() {
        Logger.i(TAG, "Requested " + loadable.board + ", " + loadable.no);

        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, cachedPosts,
                new Response.Listener<List<Post>>() {
                    @Override
                    public void onResponse(List<Post> list) {
                        Loader.this.request = null;
                        onData(list);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Loader.this.request = null;
                        onError(error);
                    }
                });

        ChanApplication.getVolleyRequestQueue().add(request);

        return request;
    }

    private void onData(List<Post> result) {
        if (destroyed)
            return;

        cachedPosts.clear();

        if (loadable.isThreadMode()) {
            cachedPosts.addAll(result);
        }

        postsById.clear();
        for (Post post : result) {
            postsById.append(post.no, post);
        }

        for (LoaderListener l : listeners) {
            l.onData(result, loadable.isBoardMode());
        }

        lastLoadTime = System.currentTimeMillis();
        if (loadable.isThreadMode()) {
            setTimer(result.size());
        }
    }

    private void onError(VolleyError error) {
        if (destroyed)
            return;

        cachedPosts.clear();

        Logger.e(TAG, "Error loading " + error.getMessage(), error);

        // 404 with more pages already loaded means endofline
        if ((error instanceof ServerError) && loadable.isBoardMode() && loadable.no > 0) {
            error = new EndOfLineException();
        }

        for (LoaderListener l : listeners) {
            l.onError(error);
        }

        clearTimer();
    }

    public static interface LoaderListener {
        public void onData(List<Post> result, boolean append);
        public void onError(VolleyError error);
    }
}
