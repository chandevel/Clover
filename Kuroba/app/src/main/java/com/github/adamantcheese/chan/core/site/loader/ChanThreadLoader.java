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

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderRequest;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

/**
 * A ChanThreadLoader is the loader for Loadables.
 * <p>Obtain ChanLoaders with {@link com.github.adamantcheese.chan.core.pool.ChanLoaderFactory}.
 * <p>ChanLoaders can load boards and threads, and return {@link ChanThread} objects on success, through
 * {@link ChanLoaderCallback}.
 * <p>For threads timers can be started with {@link #setTimer()} to do a request later.
 */
public class ChanThreadLoader
        implements Response.ErrorListener, Response.Listener<ChanLoaderResponse> {
    private static final String TAG = "ChanThreadLoader";
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private static final int[] WATCH_TIMEOUTS = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300, 600, 1800, 3600};

    @Inject
    RequestQueue volleyRequestQueue;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    SavedThreadLoaderManager savedThreadLoaderManager;

    private WatchManager watchManager;

    private final List<ChanLoaderCallback> listeners = new ArrayList<>();
    private final Loadable loadable;
    private ChanThread thread;

    private ChanLoaderRequest request;

    private CompositeDisposable compositeDisposable = new CompositeDisposable();

    private int currentTimeout = 0;
    private int lastPostCount;
    private long lastLoadTime;
    private ScheduledFuture<?> pendingFuture;

    /**
     * <b>Do not call this constructor yourself, obtain ChanLoaders through {@link com.github.adamantcheese.chan.core.pool.ChanLoaderFactory}</b>
     * Also, do not use feather().instance(WatchManager.class) here because it will create a cyclic
     * dependency instantiation
     */
    public ChanThreadLoader(Loadable loadable, WatchManager watchManager) {
        this.loadable = loadable;
        this.watchManager = watchManager;

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
        listeners.remove(listener);
        compositeDisposable.clear();

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

    @Nullable
    public ChanThread getThread() {
        return thread;
    }

    /**
     * Request data for the first time.
     */
    public void requestData() {
        clearTimer();

        Disposable disposable = Single.fromCallable(this::loadSavedCopyIfExists)
                .subscribeOn(Schedulers.io())
                .subscribe(this::requestDataInternal,
                        error -> Logger.e(TAG, "Error while loading saved thread", error)
                );

        compositeDisposable.add(disposable);
    }

    private void requestDataInternal(Boolean loaded) {
        if (loaded) {
            return;
        }

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

    private boolean loadSavedCopyIfExists() {
        if (loadable.isLocal()) {
            // Do not attempt to load data from the network when viewing a saved thread use local
            // saved thread instead

            ChanThread chanThread = loadSavedThreadIfItExists();
            if (chanThread != null && chanThread.getPostsCount() > 0) {
                // HACK: When opening a pin with local thread that is not yet fully downloaded
                // we don't want to set the thread as archived/closed because it will make
                // it permanently archived (fully downloaded)
                if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.DownloadingAndViewable) {
                    chanThread.setArchived(false);
                    chanThread.setClosed(false);
                }

                thread = chanThread;

                onPreparedResponseInternal(chanThread,
                        loadable.loadableDownloadingState,
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
        clearPendingRunnable();

        if (loadable.isThreadMode() && request == null) {
            Disposable disposable = Single.fromCallable(() -> {
                ChanLoaderRequest request = getData();
                if (request == null) {
                    // Throw an exception here, because we have to do something here but we can't just
                    // return the result, because it is null and rxjava2 does not allow us to pass
                    // nulls into the reactive stream.
                    throw new NullPointerException("getData() returned null");
                }

                return request;
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(req -> request = req, error -> {
                        if (error instanceof NullPointerException) {
                            // HACK: RxJava does not allow passing null into the reactive streams. So we use
                            // an exception for a case when getData() returns null. And if getData() returned
                            // null we don't want to log that exception, therefore we use this hack to check it.
                            String message = error.getMessage();
                            // Skip NPEs with the following message
                            if (message != null && !message.contains("getData() returned null")) {
                                Logger.e(TAG, "Error while trying to get data: ", error);
                            }
                        }
                    });

            compositeDisposable.add(disposable);
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
        BackgroundUtils.ensureMainThread();

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

        pendingFuture = executor.schedule(() -> runOnUiThread(() -> {
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
        if (loadable.mode == Loadable.Mode.THREAD
                && loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded) {
            // If loadableDownloadingState is AlreadyDownloaded try to load the local thread from
            // the disk. If we couldn't do that then try to send the request to the server
            if (onThreadArchived(true, true)) {
                Logger.d(TAG, "Thread is already fully downloaded for loadable " + loadable.toString());
                return null;
            }
        }

        Logger.d(TAG, "Requested /" + loadable.boardCode + "/, " + loadable.no);

        List<Post> cached = thread == null ? new ArrayList<>() : thread.getPosts();
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

        Disposable disposable = Single.fromCallable(() -> onResponseInternal(response))
                .subscribeOn(Schedulers.io())
                .subscribe(result -> {}, error -> Logger.e(TAG, "onResponse error", error));

        compositeDisposable.add(disposable);
    }

    private Boolean onResponseInternal(ChanLoaderResponse response) {
        // The server returned us a closed or an archived thread
        if (response != null && response.op != null && (response.op.closed || response.op.archived)) {
            if (onThreadArchived(response.op.closed, response.op.archived)) {
                return true;
            }
        }

        // Normal thread, not archived/deleted/closed
        if (response == null || response.posts.isEmpty()) {
            onErrorResponse(new VolleyError("Post size is 0"));
            return false;
        }

        if (thread == null) {
            thread = new ChanThread(loadable, new ArrayList<>());
        }

        thread.setNewPosts(response.posts);
        onResponseInternalNext(response.op);
        return true;
    }

    private boolean onThreadArchived(boolean closed, boolean archived) {
        ChanThread chanThread = loadSavedThreadIfItExists();
        if (chanThread == null) {
            if (loadable != null) {
                Logger.d(TAG, "Thread " + loadable.no + " is archived but we don't have a local copy of the thread");
            }

            // We don't have this thread locally saved, so return false and DO NOT SET thread to
            // chanThread because this will close this thread (user will see 404 not found error)
            // which we don't want.
            return false;
        }

        Logger.d(TAG,
                "Thread " + chanThread.getLoadable().no + " is archived (" + archived + ") or closed (" + closed + ")"
        );
        thread = chanThread;

        // If saved thread was not found or it has no posts (deserialization error) switch to
        // the error route
        if (chanThread.getPostsCount() > 0) {
            final SavedThread savedThread = watchManager.findSavedThreadByLoadableId(chanThread.getLoadableId());

            // Update SavedThread info in the database and in the watchManager.
            // Set isFullyDownloaded and isStopped to true so we can stop downloading it and stop
            // showing the download thread animated icon.
            runOnUiThread(() -> {
                if (savedThread != null && !savedThread.isFullyDownloaded) {
                    updateThreadAsDownloaded(archived, chanThread, savedThread);
                }
            });

            // Otherwise pass it to the response parse method
            onPreparedResponseInternal(chanThread,
                    Loadable.LoadableDownloadingState.AlreadyDownloaded,
                    closed,
                    archived
            );
            return true;
        } else {
            Logger.d(TAG, "Thread " + chanThread.getLoadable().no + " has no posts");
        }

        return false;
    }

    private void updateThreadAsDownloaded(boolean archived, ChanThread chanThread, SavedThread savedThread) {
        savedThread.isFullyDownloaded = true;
        savedThread.isStopped = true;

        chanThread.updateLoadableState(Loadable.LoadableDownloadingState.AlreadyDownloaded);
        watchManager.createOrUpdateSavedThread(savedThread);

        Pin pin = watchManager.findPinByLoadableId(savedThread.loadableId);
        if (pin == null) {
            pin = databaseManager.runTask(databaseManager.getDatabasePinManager()
                    .getPinByLoadableId(savedThread.loadableId));
        }

        if (pin == null) {
            throw new RuntimeException("Wtf? We have saved thread but we don't have a pin associated with it?");
        }

        pin.archived = archived;
        pin.watching = false;

        // Trigger the drawer to be updated so the downloading icon is updated as well
        watchManager.updatePin(pin);

        databaseManager.runTask(() -> {
            databaseManager.getDatabaseSavedThreadManager()
                    .updateThreadStoppedFlagByLoadableId(savedThread.loadableId, true)
                    .call();
            databaseManager.getDatabaseSavedThreadManager()
                    .updateThreadFullyDownloadedByLoadableId(savedThread.loadableId)
                    .call();

            return null;
        });

        Logger.d(TAG, "Successfully updated thread " + chanThread.getLoadable().no + " as fully downloaded");
    }

    private void onPreparedResponseInternal(
            ChanThread chanThread, Loadable.LoadableDownloadingState state, boolean closed, boolean archived
    ) {
        Post.Builder fakeOp = new Post.Builder();
        Post savedOp = chanThread.getOp();

        thread.setClosed(closed);
        thread.setArchived(archived);

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

    private void onResponseInternalNext(Post.Builder fakeOp) {
        processResponse(fakeOp);

        if (TextUtils.isEmpty(loadable.title)) {
            loadable.setTitle(PostHelper.getTitle(thread.getOp(), loadable));
        }

        for (Post post : thread.getPosts()) {
            post.setTitle(loadable.title);
        }

        lastLoadTime = System.currentTimeMillis();

        int postCount = thread.getPostsCount();
        if (postCount > lastPostCount) {
            lastPostCount = postCount;
            currentTimeout = 0;
        } else {
            currentTimeout = Math.min(currentTimeout + 1, WATCH_TIMEOUTS.length - 1);
        }

        runOnUiThread(() -> {
            for (ChanLoaderCallback l : listeners) {
                l.onChanLoaderData(thread);
            }
        });
    }

    /**
     * Final processing of a response that needs to happen on the main thread.
     */
    private void processResponse(Post.Builder fakeOp) {
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
                Logger.e(TAG, "Thread has no op!");
            }
        }
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        request = null;

        Disposable disposable = Single.fromCallable(() -> {
            // Thread was deleted (404), try to load a saved copy (if we have it)
            if (error.networkResponse != null && error.networkResponse.statusCode == 404 && loadable != null
                    && loadable.mode == Loadable.Mode.THREAD) {
                Logger.d(TAG, "Got 404 status for a thread " + loadable.no);

                ChanThread chanThread = loadSavedThreadIfItExists();
                if (chanThread != null && chanThread.getPostsCount() > 0) {
                    thread = chanThread;

                    Logger.d(TAG,
                            "Successfully loaded local thread " + loadable.no + " from disk, isClosed = "
                                    + chanThread.isClosed() + ", isArchived = " + chanThread.isArchived()
                    );

                    onPreparedResponseInternal(chanThread,
                            Loadable.LoadableDownloadingState.AlreadyDownloaded,
                            chanThread.isClosed(),
                            chanThread.isArchived()
                    );
                    return false;
                }
            }

            return true;
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(result -> {
            if (!result) {
                return;
            }

            Logger.i(TAG, "Loading error", error);
            clearTimer();
            ChanLoaderException loaderException = new ChanLoaderException(error);

            for (ChanLoaderCallback l : listeners) {
                l.onChanLoaderError(loaderException);
            }
        });

        compositeDisposable.add(disposable);
    }

    /**
     * Loads a saved thread if it exists
     */
    @Nullable
    private ChanThread loadSavedThreadIfItExists() {
        Loadable loadable = getLoadable();
        if (loadable == null) {
            Logger.d(TAG, "Could not get current loadable, it's null");
            return null;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            Logger.d(TAG, "Could not find pin for loadable " + loadable.toString());
            return null;
        }

        if (!PinType.hasDownloadFlag(pin.pinType)) {
            Logger.d(TAG, "Pin has no DownloadPosts flag");
            return null;
        }

        SavedThread savedThread = getSavedThreadByThreadLoadable(loadable);
        if (savedThread == null) {
            Logger.d(TAG, "Could not find savedThread for loadable " + loadable.toString());
            return null;
        }

        return savedThreadLoaderManager.loadSavedThread(loadable);
    }

    @Nullable
    private SavedThread getSavedThreadByThreadLoadable(Loadable loadable) {
        return databaseManager.runTask(() -> {
            Pin pin = databaseManager.getDatabasePinManager().getPinByLoadableId(loadable.id).call();
            if (pin == null) {
                Logger.e(TAG, "Could not find pin by loadableId = " + loadable.id);
                return null;
            }

            return databaseManager.getDatabaseSavedThreadManager().getSavedThreadByLoadableId(pin.loadable.id).call();
        });
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

    public static class ChanLoaderException
            extends Exception {
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
            } else if (volleyError instanceof NetworkError || volleyError instanceof TimeoutError
                    || volleyError instanceof ParseError || volleyError instanceof AuthFailureError) {
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
