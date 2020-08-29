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
import com.github.adamantcheese.chan.core.base.ModularResult;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedThreadManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderParser;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtils.HttpCodeException;
import com.github.adamantcheese.chan.utils.NetUtils.JsonResult;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.AlreadyDownloaded;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.DownloadingAndViewable;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.Mode.THREAD;
import static com.github.adamantcheese.chan.utils.StringUtils.maskPostNo;

/**
 * A ChanThreadLoader is the loader for Loadables.
 * <p>Obtain ChanLoaders with {@link ChanLoaderManager}.
 * <p>ChanLoaders can load boards and threads, and return {@link ChanThread} objects on success, through
 * {@link ChanLoaderCallback}.
 * <p>For threads timers can be started with {@link #setTimer()} to do a request later.
 */
public class ChanThreadLoader {
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private static final int[] WATCH_TIMEOUTS = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};
    private static final Scheduler backgroundScheduler = Schedulers.from(executor);

    private final List<ChanLoaderCallback> listeners = new CopyOnWriteArrayList<>();
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

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
    private DatabaseSavedThreadManager databaseSavedThreadManager;

    @Inject
    private DatabaseLoadableManager databaseLoadableManager;

    @Inject
    private SavedThreadLoaderManager savedThreadLoaderManager;

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
        BackgroundUtils.ensureMainThread();
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
        compositeDisposable.clear();

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

        Disposable disposable = Single.fromCallable(this::loadSavedCopyIfExists)
                .subscribeOn(backgroundScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::requestDataInternal, error -> {
                    Logger.e(ChanThreadLoader.this, "Error while loading saved thread", error);

                    notifyAboutError(error instanceof Exception ? (Exception) error : new Exception(error));
                });

        compositeDisposable.add(disposable);
    }

    private void requestDataInternal(Boolean loaded) {
        BackgroundUtils.ensureMainThread();

        if (loaded) {
            return;
        }

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

        requestMoreDataInternal();
    }

    private boolean loadSavedCopyIfExists() {
        BackgroundUtils.ensureBackgroundThread();

        if (loadable.isLocal()) {
            // Do not attempt to load data from the network when viewing a saved thread use local
            // saved thread instead

            ChanThread chanThread = loadSavedThreadIfItExists();
            if (chanThread != null && chanThread.getPostsCount() > 0) {
                // HACK: When opening a pin with local thread that is not yet fully downloaded
                // we don't want to set the thread as archived/closed because it will make
                // it permanently archived (fully downloaded)
                if (loadable.getLoadableDownloadingState() == DownloadingAndViewable) {
                    chanThread.setArchived(false);
                    chanThread.setClosed(false);
                }

                thread = chanThread;

                onPreparedResponseInternal(chanThread,
                        loadable.getLoadableDownloadingState(),
                        chanThread.isClosed(),
                        chanThread.isArchived()
                );

                return true;
            }
        }

        return false;
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
            compositeDisposable.add(requestMoreDataInternal());
            return true;
        } else {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Disposable requestMoreDataInternal() {
        return Single.fromCallable(() -> {
            getData();
            if (call == null) {
                return ModularResult.error(new ThreadAlreadyArchivedException());
            }

            return ModularResult.value(call);
        }).subscribeOn(backgroundScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (result instanceof ModularResult.Error) {
                Throwable error = ((ModularResult.Error<Throwable>) result).getError();
                if (error instanceof ThreadAlreadyArchivedException) {
                    return;
                }
                notifyAboutError(error instanceof Exception ? (Exception) error : new Exception(error));
            } else {
                call = ((ModularResult.Value<Call>) result).getValue();
            }
        }, error -> notifyAboutError(error instanceof Exception ? (Exception) error : new Exception(error)));
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
        Logger.d(this, "Scheduled reload in " + watchTimeout + "s");

        pendingFuture = executor.schedule(() -> BackgroundUtils.runOnMainThread(() -> {
            pendingFuture = null;
            requestMoreData();
        }), watchTimeout, TimeUnit.SECONDS);
    }

    public void clearTimer() {
        BackgroundUtils.ensureMainThread();

        currentTimeout = 0;
        clearPendingRunnable();
    }

    /**
     * Get the time in milliseconds until another loadMore is recommended
     */
    public long getTimeUntilLoadMore() {
        BackgroundUtils.ensureMainThread();

        if (call != null) {
            return 0L;
        } else {
            long waitTime = WATCH_TIMEOUTS[Math.max(0, currentTimeout)] * 1000L;
            return lastLoadTime + waitTime - System.currentTimeMillis();
        }
    }

    private void getData() {
        BackgroundUtils.ensureBackgroundThread();

        if (loadable.mode == THREAD && loadable.getLoadableDownloadingState() == AlreadyDownloaded) {
            // If loadableDownloadingState is AlreadyDownloaded try to load the local thread from
            // the disk. If we couldn't do that then try to send the request to the server
            if (onThreadArchived(true, true)) {
                Logger.d(this, "Thread is already fully downloaded for loadable " + loadable.toString());
                call = null;
                return;
            }
        }

        Logger.d(this, "Requested /" + loadable.boardCode + "/, " + maskPostNo(loadable.no));

        List<Post> cached;
        synchronized (this) {
            cached = thread == null ? new ArrayList<>() : thread.getPosts();
        }

        call = NetUtils.makeJsonRequest(getChanUrl(loadable), new JsonResult<ChanLoaderResponse>() {
            @Override
            public void onJsonFailure(Exception e) {
                onErrorResponse(e);
            }

            @Override
            public void onJsonSuccess(ChanLoaderResponse result) {
                clearTimer();
                onResponse(result);
            }
        }, new ChanReaderParser(loadable, cached));
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

        Disposable disposable = Single.fromCallable(() -> onResponseInternal(response))
                .subscribeOn(backgroundScheduler)
                .subscribe(result -> { }, error -> {
                    Logger.e(ChanThreadLoader.this, "onResponse error", error);

                    notifyAboutError(error instanceof Exception ? (Exception) error : new Exception(error));
                });

        compositeDisposable.add(disposable);
    }

    private Boolean onResponseInternal(ChanLoaderResponse response) {
        BackgroundUtils.ensureBackgroundThread();

        // The server returned us a closed or an archived thread
        if (response != null && response.op != null && (response.op.closed || response.op.archived)) {
            if (onThreadArchived(response.op.closed, response.op.archived)) {
                return true;
            }
        }

        // Normal thread, not archived/deleted/closed
        if (response == null || response.posts == null || response.posts.isEmpty()) {
            onErrorResponse(new Exception("Post size is 0"));
            return false;
        }

        synchronized (this) {
            if (thread == null) {
                thread = new ChanThread(loadable, new ArrayList<>());
            }

            thread.setNewPosts(response.posts);
        }

        onResponseInternalNext(response.op);
        return true;
    }

    private boolean onThreadArchived(boolean closed, boolean archived) {
        BackgroundUtils.ensureBackgroundThread();

        ChanThread chanThread = loadSavedThreadIfItExists();
        if (chanThread == null) {
            Logger.d(this,
                    "Thread " + maskPostNo(loadable.no) + " is archived but we don't have a local copy of the thread"
            );

            // We don't have this thread locally saved, so return false and DO NOT SET thread to
            // chanThread because this will close this thread (user will see 404 not found error)
            // which we don't want.
            return false;
        }

        Logger.d(this,
                "Thread " + maskPostNo(chanThread.getLoadable().no) + " is archived (" + archived + ") or closed ("
                        + closed + ")"
        );

        synchronized (this) {
            thread = chanThread;
        }

        // If saved thread was not found or it has no posts (deserialization error) switch to
        // the error route
        if (chanThread.getPostsCount() > 0) {
            // Update SavedThread info in the database and in the watchManager.
            // Set isFullyDownloaded and isStopped to true so we can stop downloading it and stop
            // showing the download thread animated icon.
            BackgroundUtils.runOnMainThread(() -> {
                final SavedThread savedThread = watchManager.findSavedThreadByLoadableId(chanThread.getLoadableId());

                if (savedThread != null && !savedThread.isFullyDownloaded) {
                    updateThreadAsDownloaded(archived, chanThread, savedThread);
                }
            });

            // Otherwise pass it to the response parse method
            onPreparedResponseInternal(chanThread, AlreadyDownloaded, closed, archived);
            return true;
        } else {
            Logger.d(this, "Thread " + maskPostNo(chanThread.getLoadable().no) + " has no posts");
        }

        return false;
    }

    private void updateThreadAsDownloaded(boolean archived, ChanThread chanThread, SavedThread savedThread) {
        BackgroundUtils.ensureMainThread();

        savedThread.isFullyDownloaded = true;
        savedThread.isStopped = true;

        chanThread.updateLoadableState(AlreadyDownloaded);
        watchManager.createOrUpdateSavedThread(savedThread);

        Pin pin = watchManager.findPinByLoadableId(savedThread.loadableId);
        if (pin == null) {
            pin = DatabaseUtils.runTask(databasePinManager.getPinByLoadableId(savedThread.loadableId));
        }

        if (pin == null) {
            throw new RuntimeException("Wtf? We have saved thread but we don't have a pin associated with it?");
        }

        pin.archived = archived;
        pin.watching = false;

        // Trigger the drawer to be updated so the downloading icon is updated as well
        watchManager.updatePin(pin);

        DatabaseUtils.runTask(() -> {
            databaseSavedThreadManager.updateThreadStoppedFlagByLoadableId(savedThread.loadableId, true).call();
            databaseSavedThreadManager.updateThreadFullyDownloadedByLoadableId(savedThread.loadableId).call();

            return null;
        });

        Logger.d(this,
                "Successfully updated thread " + maskPostNo(chanThread.getLoadable().no) + " as fully downloaded"
        );
    }

    private void onPreparedResponseInternal(
            ChanThread chanThread, Loadable.LoadableDownloadingState state, boolean closed, boolean archived
    ) {
        BackgroundUtils.ensureBackgroundThread();

        synchronized (this) {
            if (thread == null) {
                throw new IllegalStateException("thread is null");
            }

            thread.setClosed(closed);
            thread.setArchived(archived);
        }

        Post.Builder fakeOp = new Post.Builder();
        Post savedOp = chanThread.getOp();

        fakeOp.closed(closed);
        fakeOp.archived(archived);
        fakeOp.sticky(savedOp.isSticky());
        fakeOp.replies(savedOp.getReplies());
        fakeOp.images(savedOp.getImagesCount());
        fakeOp.uniqueIps(savedOp.getUniqueIps());
        fakeOp.lastModified(savedOp.getLastModified());

        chanThread.updateLoadableState(state);
        onResponseInternalNext(fakeOp);
    }

    private synchronized void onResponseInternalNext(Post.Builder fakeOp) {
        BackgroundUtils.ensureBackgroundThread();

        if (thread == null) {
            throw new IllegalStateException("thread is null");
        }

        ChanThread localThread = thread;
        processResponse(fakeOp);

        loadable.title = PostHelper.getTitle(localThread.getOp(), loadable);
        loadable.thumbnailUrl = localThread.getOp().image().getThumbnailUrl();

        for (Post post : localThread.getPosts()) {
            post.setTitle(loadable.title);
        }

        lastLoadTime = System.currentTimeMillis();

        int postCount = localThread.getPostsCount();
        if (postCount > lastPostCount) {
            lastPostCount = postCount;
            currentTimeout = 0;
        } else {
            currentTimeout = Math.min(currentTimeout + 1, WATCH_TIMEOUTS.length - 1);
        }

        DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable));

        BackgroundUtils.runOnMainThread(() -> {
            for (ChanLoaderCallback l : listeners) {
                l.onChanLoaderData(localThread);
            }
        });
    }

    /**
     * Final processing of a response that needs to happen on the main thread.
     */
    private synchronized void processResponse(Post.Builder fakeOp) {
        BackgroundUtils.ensureBackgroundThread();

        if (thread == null) {
            throw new NullPointerException("thread is null during processResponse");
        }

        if (loadable.isThreadMode() && thread.getPostsCount() > 0) {
            // Replace some op parameters to the real op (index 0).
            // This is done on the main thread to avoid race conditions.
            Post realOp = thread.getOp();
            if (fakeOp != null) {
                realOp.setClosed(fakeOp.closed);
                realOp.setArchived(fakeOp.archived);
                realOp.setSticky(fakeOp.sticky);
                realOp.setReplies(fakeOp.replies);
                realOp.setImagesCount(fakeOp.imagesCount);
                realOp.setUniqueIps(fakeOp.uniqueIps);
                realOp.setLastModified(fakeOp.lastModified);

                thread.setClosed(realOp.isClosed());
                thread.setArchived(realOp.isArchived());
            } else {
                Logger.e(this, "Thread has no op!");
            }
        }
    }

    private void onErrorResponse(Exception error) {
        call = null;

        Disposable disposable = Single.fromCallable(() -> {
            BackgroundUtils.ensureBackgroundThread();

            // Thread was deleted (404), try to load a saved copy (if we have it)
            if (error instanceof HttpCodeException && ((HttpCodeException) error).code == 404
                    && loadable.mode == THREAD) {
                Logger.d(ChanThreadLoader.this, "Got 404 status for a thread " + maskPostNo(loadable.no));

                ChanThread chanThread = loadSavedThreadIfItExists();
                if (chanThread != null && chanThread.getPostsCount() > 0) {
                    synchronized (this) {
                        thread = chanThread;
                    }

                    Logger.d(ChanThreadLoader.this,
                            "Successfully loaded local thread " + maskPostNo(loadable.no) + " from disk, isClosed = "
                                    + chanThread.isClosed() + ", isArchived = " + chanThread.isArchived()
                    );

                    onPreparedResponseInternal(chanThread,
                            AlreadyDownloaded,
                            chanThread.isClosed(),
                            chanThread.isArchived()
                    );

                    // We managed to load local thread, do no need to show the error screen
                    return false;
                }
            }

            // No local thread, show the error screen
            return true;
        }).subscribeOn(backgroundScheduler).observeOn(AndroidSchedulers.mainThread()).subscribe(showError -> {
            if (!showError) {
                return;
            }

            Logger.e(ChanThreadLoader.this, "Loading error", error);
            notifyAboutError(error);
        }, throwable -> {
            Logger.e(ChanThreadLoader.this, "Loading unhandled error", throwable);
            notifyAboutError(createError(throwable));
        });

        compositeDisposable.add(disposable);
    }

    private Exception createError(Throwable throwable) {
        if (throwable instanceof JsonSyntaxException) {
            return new Exception("Error while trying to load local thread", throwable);
        }

        return new Exception("Unhandled exception", throwable);
    }

    private void notifyAboutError(Exception exception) {
        BackgroundUtils.ensureMainThread();

        clearTimer();
        ChanLoaderException loaderException = new ChanLoaderException(exception);

        for (ChanLoaderCallback l : listeners) {
            l.onChanLoaderError(loaderException);
        }
    }

    /**
     * Loads a saved thread if it exists
     */
    @Nullable
    private ChanThread loadSavedThreadIfItExists() {
        BackgroundUtils.ensureBackgroundThread();
        Loadable loadable = getLoadable();

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            Logger.d(this, "Could not find pin for loadable " + loadable.toString());
            return null;
        }

        if (!PinType.hasDownloadFlag(pin.pinType)) {
            Logger.d(this, "Pin has no DownloadPosts flag");
            return null;
        }

        SavedThread savedThread = getSavedThreadByThreadLoadable(loadable);
        if (savedThread == null) {
            Logger.d(this, "Could not find savedThread for loadable " + loadable.toString());
            return null;
        }

        return savedThreadLoaderManager.loadSavedThread(loadable);
    }

    @Nullable
    private SavedThread getSavedThreadByThreadLoadable(Loadable loadable) {
        BackgroundUtils.ensureBackgroundThread();

        return DatabaseUtils.runTask(() -> {
            Pin pin = databasePinManager.getPinByLoadableId(loadable.id).call();
            if (pin == null) {
                Logger.e(ChanThreadLoader.this, "Could not find pin by loadableId = " + loadable.id);
                return null;
            }

            return databaseSavedThreadManager.getSavedThreadByLoadableId(pin.loadable.id).call();
        });
    }

    private void clearPendingRunnable() {
        BackgroundUtils.ensureMainThread();

        if (pendingFuture != null) {
            Logger.d(this, "Cleared runnable");
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
        private Exception exception;

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
            } else if (exception instanceof JsonParseException) {
                errorMessage = R.string.thread_load_failed_local_thread_parsing;
            } else if (exception instanceof MalformedJsonException) {
                errorMessage = R.string.thread_load_failed_parsing;
            }

            return errorMessage;
        }
    }

    private static class ThreadAlreadyArchivedException
            extends Exception {
        public ThreadAlreadyArchivedException() {
            super("Thread already archived");
        }
    }
}
