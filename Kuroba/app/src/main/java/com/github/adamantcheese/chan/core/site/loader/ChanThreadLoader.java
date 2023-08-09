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

import static com.github.adamantcheese.chan.Chan.instance;
import static java.util.concurrent.TimeUnit.SECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.net.ProgressResponseBody.ProgressListener;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderParser;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import okhttp3.Call;
import okhttp3.HttpUrl;

/**
 * A ChanThreadLoader is the loader for Loadables.
 * <p>Obtain ChanLoaders with {@link ChanLoaderManager} for either board catalogs or threads.
 * <p>ChanLoaders return {@link ChanThread} objects on success, through {@link ResponseResult<ChanThread>}.
 * <p>For threads, timers can be started with {@link #setTimer()} to do a request later.
 */
public class ChanThreadLoader {
    private static final int[] WATCH_TIMEOUTS = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    private final List<ResponseResult<ChanThread>> listeners = new CopyOnWriteArrayList<>();
    private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();

    @NonNull
    private final Loadable loadable;
    @Nullable
    private ChanThread thread;
    @Nullable
    private Call call;
    @Nullable
    private ScheduledFuture<?> pendingFuture;

    private int currentTimeout = 0;
    private int lastPostCount;
    private long lastLoadTime;

    /**
     * <b>Do not call this constructor yourself, obtain ChanLoaders through {@link ChanLoaderManager}</b>
     */
    public ChanThreadLoader(@NonNull Loadable loadable) {
        this.loadable = loadable;
    }

    /**
     * Add a LoaderListener
     *
     * @param listener the listener to add
     */
    public void addListener(ResponseResult<ChanThread> listener) {
        listeners.add(listener);
    }

    public void addProgressListener(ProgressListener listener) {
        progressListeners.add(listener);
    }

    public void removeProgressListener(ProgressListener listener) {
        progressListeners.remove(listener);
    }

    /**
     * Remove a LoaderListener
     *
     * @param listener the listener to remove
     * @return true if there are no more listeners, false otherwise
     */
    public boolean removeListener(ResponseResult<ChanThread> listener) {
        listeners.remove(listener);

        if (listeners.isEmpty()) {
            clearTimer();
            if (call != null) {
                call.cancel();
                call = null;
            }
            return true;
        } else {
            return false;
        }
    }

    public void clearListeners()
    {
        listeners.clear();
        clearTimer();
        if (call != null) {
            call.cancel();
            call = null;
        }
    }

    @Nullable
    public ChanThread getThread() {
        return thread;
    }

    /**
     * Request data for the first time.
     */
    public void requestFreshData() {
        BackgroundUtils.ensureMainThread();
        clearTimer();

        if (call != null) {
            call.cancel();
            call = null;
        }

        if (loadable.isCatalogMode()) {
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
        }

        currentTimeout = -1;

        synchronized (this) {
            thread = null;
        }

        call = getData();
    }

    /**
     * Request more data. This only works for thread loaders.<br>
     * This clears any pending pending timers, created with {@link #setTimer()}.
     */

    public void requestAdditionalData() {
        BackgroundUtils.ensureMainThread();
        clearPendingRunnable();

        if (loadable.isThreadMode() && call == null) {
            call = getData();
        }
    }

    @NonNull
    public Loadable getLoadable() {
        return loadable;
    }

    public void setTimer() {
        BackgroundUtils.ensureMainThread();
        clearPendingRunnable();

        int watchTimeout = WATCH_TIMEOUTS[currentTimeout];

        pendingFuture =
                BackgroundUtils.backgroundScheduledService.schedule(() -> BackgroundUtils.runOnMainThread(() -> {
                    pendingFuture = null;
                    requestAdditionalData();
                }), watchTimeout, SECONDS);
    }

    public void clearTimer() {
        currentTimeout = 0;
        clearPendingRunnable();
    }

    private void clearPendingRunnable() {
        if (pendingFuture != null) {
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    /**
     * @return milliseconds until another requestAdditionalData is recommended
     */
    public long getTimeUntilLoadMore() {
        long waitTime = SECONDS.toMillis(WATCH_TIMEOUTS[Math.max(0, currentTimeout)]);
        return call != null ? 0L : lastLoadTime + waitTime - System.currentTimeMillis();
    }

    private Call getData() {
        List<Post> cachedClones = new ArrayList<>();
        synchronized (this) {
            List<Post> cached = thread == null ? new ArrayList<>() : thread.getPosts();
            for (Post p : cached) {
                cachedClones.add(p.clone());
            }
        }

        return NetUtils.makeJsonRequest(
                getChanUrl(loadable),
                new ResponseResult<ChanLoaderResponse>() {
                    @Override
                    public void onFailure(Exception e) {
                        notifyAboutError(e);
                    }

                    @Override
                    public void onSuccess(ChanLoaderResponse result) {
                        if (result.posts.isEmpty()) {
                            notifyAboutError(new Exception("No posts in thread!"));
                        } else {
                            onResponseInternal(result);
                        }
                    }
                },
                new ChanReaderParser(loadable, cachedClones, null),
                null,
                (source, bytesRead, contentLength, start, done) -> {
                    for (ProgressListener listener : progressListeners) {
                        listener.onDownloadProgress(source, bytesRead, contentLength, start, done);
                    }
                }
        );
        // TODO for cached thread loading, use a cacheControl with maxStale set so it loads a cached response
    }

    private HttpUrl getChanUrl(Loadable loadable) {
        if (loadable.isThreadMode()) {
            return loadable.board.site.endpoints().thread(loadable);
        } else if (loadable.isCatalogMode()) {
            return loadable.board.site.endpoints().catalog(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }
    }

    private void onResponseInternal(ChanLoaderResponse response) {
        BackgroundUtils.ensureBackgroundThread();

        call = null;
        clearTimer();

        synchronized (this) {
            if (thread == null) {
                thread = new ChanThread(loadable, new ArrayList<>());
            }

            thread.setNewPosts(response.posts);
        }

        ChanThread localThread = thread;
        if (loadable.isThreadMode() && thread.getPosts().size() > 0) {
            // Replace some op parameters to the real op (index 0).
            // This is done on the main thread to avoid race conditions.
            Post realOp = thread.getOp();
            if (response.op != null) {
                realOp.closed = response.op.closed;
                realOp.archived = response.op.archived;
                realOp.sticky = response.op.sticky;
                realOp.replies = response.op.replies;
                realOp.imagesCount = response.op.imagesCount;
                realOp.uniqueIps = response.op.uniqueIps;
                realOp.lastModified = response.op.lastModified;
            } else {
                Logger.e(this, "Thread has no op!");
            }
        }

        loadable.title = PostHelper.getTitle(localThread.getOp(), loadable);
        try {
            loadable.thumbnailUrl = localThread.getOp().image().getThumbnailUrl();
        } catch (Exception e) {
            loadable.thumbnailUrl = null;
        }

        for (Post post : localThread.getPosts()) {
            post.title = loadable.title;
        }

        lastLoadTime = System.currentTimeMillis();

        int postCount = localThread.getPosts().size();
        if (postCount > lastPostCount) {
            // fresh posts, reset timer to minimum 10 seconds, or if sticky 30
            lastPostCount = postCount;
            currentTimeout = localThread.getOp().sticky ? 3 : 0;
        } else {
            // no new posts, increase timer; if -1, this becomes 0 in the case of a fresh load
            currentTimeout = Math.min(currentTimeout + 1, WATCH_TIMEOUTS.length - 1);
        }

        DatabaseUtils.runTaskAsync(instance(DatabaseLoadableManager.class).updateLoadable(loadable, false));

        for (ResponseResult<ChanThread> l : listeners) {
            BackgroundUtils.runOnMainThread(() -> l.onSuccess(localThread));
        }
    }

    private void notifyAboutError(Exception exception) {
        call = null;
        clearTimer();

        Logger.w(this, "Loading error", exception);

        for (ResponseResult<ChanThread> l : listeners) {
            BackgroundUtils.runOnMainThread(() -> l.onFailure(exception));
        }
    }
}
