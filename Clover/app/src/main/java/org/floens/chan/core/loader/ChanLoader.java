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
package org.floens.chan.core.loader;

import android.text.TextUtils;
import android.util.SparseArray;

import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.net.ChanReaderRequest;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ChanLoader {
    private static final String TAG = "ChanLoader";
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private static final int[] watchTimeouts = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    private final List<ChanLoaderCallback> listeners = new ArrayList<>();
    private final Loadable loadable;
    private final SparseArray<Post> postsById = new SparseArray<>();
    private ChanThread thread;

    private boolean destroyed = false;
    private boolean autoReload = false;
    private ChanReaderRequest request;

    private int currentTimeout;
    private int lastPostCount;
    private long lastLoadTime;
    private ScheduledFuture<?> pendingFuture;

    public ChanLoader(Loadable loadable) {
        this.loadable = loadable;
    }

    /**
     * Add a LoaderListener
     *
     * @param l the listener to add
     */
    public void addListener(ChanLoaderCallback l) {
        listeners.add(l);
    }

    /**
     * Remove a LoaderListener
     *
     * @param l the listener to remove
     * @return true if there are no more listeners, false otherwise
     */
    public boolean removeListener(ChanLoaderCallback l) {
        listeners.remove(l);
        if (listeners.size() == 0) {
            clearTimer();
            destroyed = true;
            if (request != null) {
                request.cancel();
            }
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
            Logger.d(TAG, "Setting autoreload to " + autoReload);
            this.autoReload = autoReload;

            if (!autoReload) {
                clearTimer();
            }
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

    /**
     * Request data for the first time.
     */
    public void requestData() {
        clearTimer();

        if (request != null) {
            request.cancel();
        }

        if (loadable.isBoardMode() || loadable.isCatalogMode()) {
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
        }

        currentTimeout = 0;
        thread = null;

        request = getData();
    }

    /**
     * Request more data
     */
    public void requestMoreData() {
        clearTimer();

        if (loadable.isBoardMode()) {
            if (request != null) {
                // finish the last board load first
                return;
            }

            loadable.no++;

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
     *
     * @return
     */
    public long getTimeUntilLoadMore() {
        if (request != null) {
            return 0L;
        } else {
            long waitTime = watchTimeouts[currentTimeout] * 1000L;
            return lastLoadTime + waitTime - Time.get();
        }
    }

    public ChanThread getThread() {
        return thread;
    }

    private void setTimer(int postCount) {
        clearTimer();

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

        if (autoReload) {
            Runnable pendingRunnable = new Runnable() {
                @Override
                public void run() {
                    AndroidUtils.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pendingFuture = null;
                            // Always reload, it's always time to reload when the timer fires
                            requestMoreData();
                        }
                    });
                }
            };

            Logger.d(TAG, "Scheduled reload in " + watchTimeouts[currentTimeout] * 1000L);
            pendingFuture = executor.schedule(pendingRunnable, watchTimeouts[currentTimeout], TimeUnit.SECONDS);
        }
    }

    private void clearTimer() {
        if (pendingFuture != null) {
            Logger.d(TAG, "Removed pending runnable");
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    private ChanReaderRequest getData() {
        Logger.i(TAG, "Requested " + loadable.board + ", " + loadable.no);

        List<Post> cached = thread == null ? new ArrayList<Post>() : thread.posts;
        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, cached,
                new Response.Listener<List<Post>>() {
                    @Override
                    public void onResponse(List<Post> list) {
                        ChanLoader.this.request = null;
                        onData(list);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        ChanLoader.this.request = null;
                        onError(error);
                    }
                }
        );

        ChanApplication.getVolleyRequestQueue().add(request);

        return request;
    }

    private void onData(List<Post> result) {
        if (destroyed)
            return;

        if (thread == null) {
            thread = new ChanThread(loadable, new ArrayList<Post>());
        }

        if (loadable.isThreadMode() || loadable.isCatalogMode()) {
            thread.posts.clear();
            thread.posts.addAll(result);
            postsById.clear();
            for (Post post : result) {
                postsById.append(post.no, post);
            }
        } else if (loadable.isBoardMode()) {
            // Only add new posts
            boolean flag;
            for (Post post : result) {
                flag = true;
                for (Post cached : thread.posts) {
                    if (post.no == cached.no) {
                        flag = false;
                        break;
                    }
                }

                if (flag) {
                    thread.posts.add(post);
                    postsById.append(post.no, post);
                }
            }
        }

        if (loadable.isThreadMode() && thread.posts.size() > 0) {
            thread.op = thread.posts.get(0);
            thread.closed = thread.op.closed;
            thread.archived = thread.op.archived;
        }

        if (TextUtils.isEmpty(loadable.title)) {
            if (thread.op != null) {
                loadable.generateTitle(thread.op);
            } else {
                loadable.title = "/" + loadable.board + "/";
            }
        }

        for (Post post : thread.posts) {
            post.title = loadable.title;
        }

        lastLoadTime = Time.get();

        if (loadable.isThreadMode()) {
            setTimer(result.size());
        }

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderData(thread);
        }
    }

    private void onError(VolleyError error) {
        if (destroyed)
            return;

        Logger.e(TAG, "Loading error", error);

        // 404 with more pages already loaded means endofline
        if ((error instanceof ServerError) && loadable.isBoardMode() && loadable.no > 0) {
            error = new EndOfLineException();
        }

        clearTimer();

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderError(error);
        }
    }

    public interface ChanLoaderCallback {
        void onChanLoaderData(ChanThread result);

        void onChanLoaderError(VolleyError error);
    }
}
