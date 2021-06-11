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

import android.util.MalformedJsonException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.HttpCodeException;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderParser;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * A ChanThreadLoader is the loader for Loadables.
 * <p>Obtain ChanLoaders with {@link ChanLoaderManager}.
 * <p>ChanLoaders can load boards and threads, and return {@link ChanThread} objects on success, through
 * {@link ChanLoaderCallback}.
 * <p>For threads timers can be started with {@link #setTimer()} to do a request later.
 */
public class ChanThreadLoader {
    private static final int[] WATCH_TIMEOUTS = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    private final List<ChanLoaderCallback> listeners = new CopyOnWriteArrayList<>();

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

    @Inject
    private WatchManager watchManager;

    @Inject
    private DatabasePinManager databasePinManager;

    @Inject
    private DatabaseLoadableManager databaseLoadableManager;

    /**
     * <b>Do not call this constructor yourself, obtain ChanLoaders through {@link ChanLoaderManager}</b>
     */
    public ChanThreadLoader(@NonNull Loadable loadable) {
        this.loadable = loadable;
        inject(this);
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
        BackgroundUtils.ensureMainThread();

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

    @Nullable
    public ChanThread getThread() {
        return thread;
    }

    /**
     * Request data for the first time.
     */
    public void requestData() {
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
     *
     * @return {@code true} if a new request was started, {@code false} otherwise.
     */
    public boolean requestMoreData() {
        BackgroundUtils.ensureMainThread();
        clearPendingRunnable();

        if (loadable.isThreadMode() && call == null) {
            call = getData();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Request more data if {@link #getTimeUntilLoadMore()} is negative.
     */
    public boolean loadMoreIfTime() {
        BackgroundUtils.ensureMainThread();
        return getTimeUntilLoadMore() < 0L && requestMoreData();
    }

    public void quickLoad() {
        BackgroundUtils.ensureMainThread();

        ChanThread localThread;
        synchronized (this) {
            if (thread == null) {
                throw new IllegalStateException("Cannot quick load without already loaded thread");
            }

            localThread = thread;
        }

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderData(localThread);
        }

        requestMoreData();
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
        if (call != null) {
            return 0L;
        } else {
            long waitTime = WATCH_TIMEOUTS[Math.max(0, currentTimeout)] * 1000L;
            return lastLoadTime + waitTime - System.currentTimeMillis();
        }
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
                        notifyAboutError(new ChanLoaderException(e));
                    }

                    @Override
                    public void onSuccess(ChanLoaderResponse result) {
                        clearTimer();
                        BackgroundUtils.runOnBackgroundThread(() -> onResponse(result));
                    }
                },
                new ChanReaderParser(loadable, cachedClones, null),
                // todo change this so that If-Modified-Since takes care of stuff
                // cache this for the amount of time of the current timeout, minus a second to ensure it is purged upon the next request
                new CacheControl.Builder().maxAge(WATCH_TIMEOUTS[Math.max(0, currentTimeout)] - 1, TimeUnit.SECONDS)
                        .build()
        );
    }

    private HttpUrl getChanUrl(Loadable loadable) {
        HttpUrl url;

        if (loadable.site == null) {
            throw new NullPointerException("Loadable.site == null");
        }

        if (loadable.board == null) {
            throw new NullPointerException("Loadable.board == null");
        }

        if (loadable.isThreadMode()) {
            url = loadable.site.endpoints().thread(loadable);
        } else if (loadable.isCatalogMode()) {
            url = loadable.site.endpoints().catalog(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }
        return url;
    }

    private void onResponse(ChanLoaderResponse response) {
        call = null;

        try {
            if (response.posts.isEmpty()) {
                throw new Exception("No posts in thread!");
            }

            onResponseInternal(response);
        } catch (Throwable e) {
            Logger.e(ChanThreadLoader.this, "onResponse error", e);
            notifyAboutError(new ChanLoaderException(e instanceof Exception ? (Exception) e : new Exception(e)));
        }
    }

    private void onResponseInternal(ChanLoaderResponse response) {
        BackgroundUtils.ensureBackgroundThread();

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
                realOp.setClosed(response.op.closed);
                realOp.setArchived(response.op.archived);
                realOp.setSticky(response.op.sticky);
                realOp.setReplies(response.op.replies);
                realOp.setImagesCount(response.op.imagesCount);
                realOp.setUniqueIps(response.op.uniqueIps);
                realOp.setLastModified(response.op.lastModified);

                thread.setClosed(realOp.isClosed());
                thread.setArchived(realOp.isArchived());
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
            post.setTitle(loadable.title);
        }

        lastLoadTime = System.currentTimeMillis();

        int postCount = localThread.getPosts().size();
        if (postCount > lastPostCount) {
            // fresh posts, reset timer to minimum 10 seconds, or if sticky 30
            lastPostCount = postCount;
            currentTimeout = localThread.getOp().isSticky() ? 3 : 0;
        } else {
            // no new posts, increase timer
            currentTimeout = Math.min(currentTimeout + 1, WATCH_TIMEOUTS.length - 1);
        }

        DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable, false));

        BackgroundUtils.runOnMainThread(() -> {
            for (ChanLoaderCallback l : listeners) {
                l.onChanLoaderData(localThread);
            }
        });
    }

    private void notifyAboutError(ChanLoaderException exception) {
        call = null;
        clearTimer();

        Logger.e(this, "Loading error", exception);

        BackgroundUtils.runOnMainThread(() -> {
            for (ChanLoaderCallback l : listeners) {
                l.onChanLoaderError(exception);
            }
        });
    }

    private void clearPendingRunnable() {
        if (pendingFuture != null) {
            pendingFuture.cancel(false);
            pendingFuture = null;
        }
    }

    public interface ChanLoaderCallback {
        void onChanLoaderData(ChanThread result);

        void onChanLoaderError(ChanLoaderException error);
    }

    public static class ChanLoaderException
            extends Exception {
        private final Exception exception;

        public ChanLoaderException(Exception exception) {
            this.exception = exception;
        }

        public boolean isNotFound() {
            return exception instanceof HttpCodeException && ((HttpCodeException) exception).isServerErrorNotFound();
        }

        public int getErrorMessage() {
            //by default, a network error has occurred if the exception field is not null
            int errorMessage = exception != null ? R.string.thread_load_failed_network : R.string.empty;
            if (exception instanceof SSLException) {
                errorMessage = R.string.thread_load_failed_ssl;
            } else if (exception instanceof HttpCodeException) {
                if (((HttpCodeException) exception).isServerErrorNotFound()) {
                    errorMessage = R.string.thread_load_failed_not_found;
                } else {
                    errorMessage = R.string.thread_load_failed_server;
                }
            } else if (exception instanceof MalformedJsonException) {
                errorMessage = R.string.thread_load_failed_parsing;
            }

            return errorMessage;
        }
    }
}
