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
package com.github.adamantcheese.chan.core.presenter;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.History;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.pool.ChanLoaderFactory;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.PostUtils;
import com.github.k1rakishou.fsaf.FileManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class ThreadPresenter
        implements ChanThreadLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback,
                   PostCellInterface.PostCellCallback, ThreadStatusCell.Callback,
                   ThreadListLayout.ThreadListLayoutPresenterCallback, ArchivesLayout.Callback {
    private static final String TAG = "ThreadPresenter";

    private static final int POST_OPTION_QUOTE = 0;
    private static final int POST_OPTION_QUOTE_TEXT = 1;
    private static final int POST_OPTION_INFO = 2;
    private static final int POST_OPTION_LINKS = 3;
    private static final int POST_OPTION_COPY_TEXT = 4;
    private static final int POST_OPTION_REPORT = 5;
    private static final int POST_OPTION_HIGHLIGHT_ID = 6;
    private static final int POST_OPTION_DELETE = 7;
    private static final int POST_OPTION_SAVE = 8;
    private static final int POST_OPTION_PIN = 9;
    private static final int POST_OPTION_SHARE = 10;
    private static final int POST_OPTION_HIGHLIGHT_TRIPCODE = 11;
    private static final int POST_OPTION_HIDE = 12;
    private static final int POST_OPTION_OPEN_BROWSER = 13;
    private static final int POST_OPTION_FILTER_TRIPCODE = 14;
    private static final int POST_OPTION_EXTRA = 15;
    private static final int POST_OPTION_REMOVE = 16;

    private final WatchManager watchManager;
    private final DatabaseManager databaseManager;
    private final ChanLoaderFactory chanLoaderFactory;
    private final PageRequestManager pageRequestManager;
    private final ThreadSaveManager threadSaveManager;
    private final FileManager fileManager;
    private final FileCacheV2 fileCacheV2;
    private final CacheHandler cacheHandler;

    private ThreadPresenterCallback threadPresenterCallback;
    private Loadable loadable;
    private ChanThreadLoader chanLoader;
    private boolean searchOpen;
    private String searchQuery;
    private boolean forcePageUpdate = true;
    private PostsFilter.Order order = PostsFilter.Order.BUMP;
    private boolean historyAdded;
    private boolean addToLocalBackHistory;
    private Context context;

    @Nullable
    private List<CancelableDownload> activePrefetches = null;

    @Inject
    public ThreadPresenter(
            WatchManager watchManager,
            DatabaseManager databaseManager,
            ChanLoaderFactory chanLoaderFactory,
            PageRequestManager pageRequestManager,
            ThreadSaveManager threadSaveManager,
            FileManager fileManager,
            FileCacheV2 fileCacheV2,
            CacheHandler cacheHandler
    ) {
        this.watchManager = watchManager;
        this.databaseManager = databaseManager;
        this.chanLoaderFactory = chanLoaderFactory;
        this.pageRequestManager = pageRequestManager;
        this.threadSaveManager = threadSaveManager;
        this.fileManager = fileManager;
        this.fileCacheV2 = fileCacheV2;
        this.cacheHandler = cacheHandler;
    }

    public void create(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;
    }

    public void showNoContent() {
        threadPresenterCallback.showEmpty();
    }

    public void bindLoadable(Loadable loadable, boolean addToLocalBackHistory) {
        if (!loadable.equals(this.loadable)) {
            if (this.loadable != null) {
                stopSavingThreadIfItIsBeingSaved(this.loadable);
            }

            if (chanLoader != null) {
                unbindLoadable();
            }

            Pin pin = watchManager.findPinByLoadableId(loadable.id);
            // TODO this isn't true anymore, because all loadables come from one location.
            if (pin != null) {
                // Use the loadable from the pin.
                // This way we can store the list position in the pin loadable,
                // and not in a separate loadable instance.
                loadable = pin.loadable;
            }

            this.loadable = loadable;
            this.addToLocalBackHistory = addToLocalBackHistory;

            startSavingThreadIfItIsNotBeingSaved(this.loadable);
            chanLoader = chanLoaderFactory.obtain(loadable, watchManager, this);
            threadPresenterCallback.showLoading();
        }
    }

    public void bindLoadable(Loadable loadable) {
        bindLoadable(loadable, true);
    }

    public void unbindLoadable() {
        if (chanLoader != null) {
            chanLoader.clearTimer();
            chanLoaderFactory.release(chanLoader, this);
            chanLoader = null;
            loadable = null;
            historyAdded = false;
            addToLocalBackHistory = true;
            cancelPrefetching();

            threadPresenterCallback.showNewPostsNotification(false, -1);
            threadPresenterCallback.showLoading();
        }
    }

    private void cancelPrefetching() {
        if (activePrefetches == null || activePrefetches.isEmpty()) {
            return;
        }

        Logger.d(TAG, "Cancel previous prefetching");

        for (CancelableDownload cancelableDownload : activePrefetches) {
            cancelableDownload.cancelPrefetch();
        }

        activePrefetches.clear();
        activePrefetches = null;
    }

    private void stopSavingThreadIfItIsBeingSaved(Loadable loadable) {
        if (ChanSettings.watchEnabled.get() && ChanSettings.watchBackground.get()
                // Do not stop prev thread saving if background watcher is enabled
                || loadable == null || loadable.mode != Loadable.Mode.THREAD) // We are in the catalog probably
        {
            return;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null // No pin for this loadable we are probably not downloading this thread
                || !PinType.hasDownloadFlag(pin.pinType)) // Pin has no downloading flag
        {
            return;
        }

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
        if (savedThread == null // We are not downloading this thread
                || loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded
                // We are viewing already saved copy of the thread
                || savedThread.isFullyDownloaded || savedThread.isStopped) {
            return;
        }

        watchManager.stopSavingThread(loadable);
        postToEventBus(new WatchManager.PinMessages.PinChangedMessage(pin));
    }

    private void startSavingThreadIfItIsNotBeingSaved(Loadable loadable) {
        if ((ChanSettings.watchEnabled.get() && ChanSettings.watchBackground.get()) || loadable == null
                || loadable.mode != Loadable.Mode.THREAD) {
            // Do not start thread saving if background watcher is enabled
            // Or if we're in the catalog
            return;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
            // No pin for this loadable we are probably not downloading this thread
            // Pin has no downloading flag
            return;
        }

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
        if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded
                || savedThread == null || savedThread.isFullyDownloaded || !savedThread.isStopped) {
            // We are viewing already saved copy of the thread
            // We are not downloading this thread
            // Thread is already fully downloaded
            // Thread saving is already in progress
            return;
        }

        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
            // Base directory for local threads does not exist or was deleted
            return;
        }

        watchManager.startSavingThread(loadable);
        postToEventBus(new WatchManager.PinMessages.PinChangedMessage(pin));
    }

    public boolean isBound() {
        return chanLoader != null;
    }

    public void requestInitialData() {
        if (chanLoader != null) {
            if (chanLoader.getThread() == null) {
                requestData();
            } else {
                chanLoader.quickLoad();
            }
        }
    }

    public void requestData() {
        if (chanLoader != null) {
            threadPresenterCallback.showLoading();
            chanLoader.requestData();
        }
    }

    public void onForegroundChanged(boolean foreground) {
        if (chanLoader != null) {
            if (foreground && isWatching()) {
                chanLoader.requestMoreDataAndResetTimer();
                if (chanLoader.getThread() != null) {
                    // Show loading indicator in the status cell
                    showPosts();
                }
            } else {
                chanLoader.clearTimer();
            }
        }
    }

    public boolean pin() {
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            if (chanLoader.getThread() == null) {
                return false;
            }

            Post op = chanLoader.getThread().getOp();
            watchManager.createPin(loadable, op, PinType.WATCH_NEW_POSTS);
            return true;
        }

        if (PinType.hasWatchNewPostsFlag(pin.pinType)) {
            pin.pinType = PinType.removeWatchNewPostsFlag(pin.pinType);

            if (PinType.hasNoFlags(pin.pinType)) {
                watchManager.deletePin(pin);
            } else {
                watchManager.updatePin(pin);
            }
        } else {
            pin.pinType = PinType.addWatchNewPostsFlag(pin.pinType);
            watchManager.updatePin(pin);
        }

        return true;
    }

    public boolean save() {
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
            boolean startedSaving = saveInternal();
            if (!startedSaving) {
                watchManager.stopSavingThread(loadable);
            }

            return startedSaving;
        }

        if (!PinType.hasWatchNewPostsFlag(pin.pinType)) {
            pin.pinType = PinType.removeDownloadNewPostsFlag(pin.pinType);
            watchManager.deletePin(pin);
        } else {
            watchManager.stopSavingThread(pin.loadable);

            // Remove the flag after stopping thread saving, otherwise we just won't find the thread
            // because the pin won't have the download flag which we check somewhere deep inside the
            // stopSavingThread() method
            pin.pinType = PinType.removeDownloadNewPostsFlag(pin.pinType);
            watchManager.updatePin(pin);
        }

        loadable.loadableDownloadingState = Loadable.LoadableDownloadingState.NotDownloading;
        return true;
    }

    private boolean saveInternal() {
        if (chanLoader.getThread() == null) {
            Logger.e(TAG, "chanLoader.getThread() == null");
            return false;
        }

        Post op = chanLoader.getThread().getOp();
        List<Post> postsToSave = chanLoader.getThread().getPosts();

        Pin oldPin = watchManager.findPinByLoadableId(loadable.id);
        if (oldPin != null) {
            // Save button is clicked and bookmark button is already pressed
            // Update old pin and start saving the thread
            if (PinType.hasDownloadFlag(oldPin.pinType)) {
                // We forgot to delete pin when cancelling thread download?
                throw new IllegalStateException("oldPin already contains DownloadFlag");
            }

            oldPin.pinType = PinType.addDownloadNewPostsFlag(oldPin.pinType);
            watchManager.updatePin(oldPin);

            if (!startSavingThreadInternal(loadable, postsToSave, oldPin)) {
                return false;
            }

            postToEventBus(new WatchManager.PinMessages.PinChangedMessage(oldPin));
        } else {
            // Save button is clicked and bookmark button is not yet pressed
            // Create new pin and start saving the thread

            // We don't want to send PinAddedMessage broadcast right away. We will send it after
            // the thread has been saved
            if (!watchManager.createPin(loadable, op, PinType.DOWNLOAD_NEW_POSTS, false)) {
                throw new IllegalStateException("Could not create pin for loadable " + loadable);
            }

            Pin newPin = watchManager.getPinByLoadable(loadable);
            if (newPin == null) {
                throw new IllegalStateException("Could not find freshly created pin by loadable " + loadable);
            }

            if (!startSavingThreadInternal(loadable, postsToSave, newPin)) {
                return false;
            }

            postToEventBus(new WatchManager.PinMessages.PinAddedMessage(newPin));
        }

        if (!ChanSettings.watchEnabled.get() || !ChanSettings.watchBackground.get()) {
            showToast(R.string.thread_layout_background_watcher_is_disabled_message, Toast.LENGTH_LONG);
        }

        return true;
    }

    private boolean startSavingThreadInternal(Loadable loadable, List<Post> postsToSave, Pin newPin) {
        if (!PinType.hasDownloadFlag(newPin.pinType)) {
            throw new IllegalStateException("newPin does not have DownloadFlag: " + newPin.pinType);
        }

        return watchManager.startSavingThread(loadable, postsToSave);
    }

    public boolean isPinned() {
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            return false;
        }

        return PinType.hasWatchNewPostsFlag(pin.pinType);
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
        if (!visible) {
            searchQuery = null;
        }

        if (chanLoader != null && chanLoader.getThread() != null) {
            showPosts();
        }
    }

    public void onSearchEntered(String entered) {
        searchQuery = entered;
        if (chanLoader != null && chanLoader.getThread() != null) {
            showPosts();
            if (TextUtils.isEmpty(entered)) {
                threadPresenterCallback.setSearchStatus(null, true, false);
            } else {
                threadPresenterCallback.setSearchStatus(entered, false, false);
            }
        }
    }

    public void setOrder(PostsFilter.Order order) {
        if (this.order != order) {
            this.order = order;
            if (chanLoader != null && chanLoader.getThread() != null) {
                scrollTo(0, false);
                showPosts();
            }
        }
    }

    public void refreshUI() {
        if (chanLoader != null && chanLoader.getThread() != null) {
            showPosts(true);
        }
    }

    public void showAlbum() {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        int[] pos = threadPresenterCallback.getCurrentPosition();
        int displayPosition = pos[0];

        List<PostImage> images = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            if (!item.images.isEmpty()) {
                images.addAll(item.images);
            }
            if (i == displayPosition) {
                index = images.size();
            }
        }

        threadPresenterCallback.showAlbum(images, index);
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    /*
     * ChanThreadLoader callbacks
     */
    @Override
    public void onChanLoaderData(ChanThread result) {
        BackgroundUtils.ensureMainThread();

        loadable.loadableDownloadingState = result.getLoadable().loadableDownloadingState;
        Logger.d(TAG, "onChanLoaderData() loadableDownloadingState = " + loadable.loadableDownloadingState.name());

        if (isWatching() && chanLoader != null) {
            chanLoader.setTimer();
        }

        showPosts();

        if (loadable.isThreadMode()) {
            int lastLoaded = loadable.lastLoaded;
            int more = 0;
            if (lastLoaded > 0) {
                for (Post p : result.getPosts()) {
                    if (p.no == lastLoaded) {
                        more = result.getPostsCount() - result.getPosts().indexOf(p) - 1;
                        break;
                    }
                }
            }

            loadable.setLastLoaded(result.getPosts().get(result.getPostsCount() - 1).no);

            if (more > 0) {
                threadPresenterCallback.showNewPostsNotification(true, more);
                //deal with any "requests" for a page update
                if (forcePageUpdate) {
                    pageRequestManager.forceUpdateForBoard(loadable.board);
                    forcePageUpdate = false;
                }
            }

            if (ChanSettings.autoLoadThreadImages.get() && !loadable.isLocal()) {
                List<PostImage> postImageList = new ArrayList<>(16);
                cancelPrefetching();

                for (Post p : result.getPosts()) {
                    if (p.images != null) {
                        for (PostImage postImage : p.images) {
                            if (cacheHandler.exists(postImage.imageUrl.toString())) {
                                continue;
                            }

                            if ((postImage.type == PostImage.Type.STATIC || postImage.type == PostImage.Type.GIF)
                                    && shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get()))
                            {
                                postImageList.add(postImage);
                            } else if (postImage.type == PostImage.Type.MOVIE
                                    && shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get()))
                            {
                                postImageList.add(postImage);
                            }
                        }
                    }
                }

                if (postImageList.size() > 0) {
                    activePrefetches = fileCacheV2.enqueueMediaPrefetchRequestBatch(
                            loadable,
                            postImageList
                    );
                }
            }
        }

        if (loadable.markedNo >= 0 && chanLoader != null) {
            Post markedPost = PostUtils.findPostById(loadable.markedNo, chanLoader.getThread());
            if (markedPost != null) {
                highlightPost(markedPost);
                scrollToPost(markedPost, false);
            }
            loadable.markedNo = -1;
        }

        storeNewPostsIfThreadIsBeingDownloaded(loadable, result.getPosts());
        addHistory();

        // Update loadable in the database
        databaseManager.runTaskAsync(databaseManager.getDatabaseLoadableManager().updateLoadable(loadable));

        if (!ChanSettings.watchEnabled.get() && !ChanSettings.watchBackground.get()
                && loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded) {
            Logger.d(TAG,
                    "Background watcher is disabled, so we need to update "
                            + "ViewThreadController's downloading icon as well as the pin in the DrawerAdapter"
            );

            Pin pin = watchManager.findPinByLoadableId(loadable.id);
            if (pin == null) {
                Logger.d(TAG, "Could not find pin with loadableId = " + loadable.id + ", it was already deleted?");
                return;
            }

            pin.isError = true;
            pin.watching = false;

            watchManager.updatePin(pin, true);
        }

        if (result.getLoadable().isCatalogMode()) {
            instance(FilterWatchManager.class).onCatalogLoad(result);
        }
    }

    private void storeNewPostsIfThreadIsBeingDownloaded(Loadable loadable, List<Post> posts) {
        if (loadable.mode != Loadable.Mode.THREAD) {
            // We are not in a thread
            return;
        }

        if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded) {
            // Do not save posts from already saved thread
            return;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            // No pin for this loadable we are probably not downloading this thread
            return;
        }

        if (!PinType.hasDownloadFlag(pin.pinType)) {
            // Pin has no downloading flag
            return;
        }

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
        if (savedThread == null || savedThread.isStopped || savedThread.isFullyDownloaded) {
            // Either the thread is not being downloaded or it is stopped or already fully downloaded
            return;
        }

        if (posts.isEmpty()) {
            // No posts to save
            return;
        }

        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
            Logger.d(TAG, "storeNewPostsIfThreadIsBeingDownloaded() LocalThreadsBaseDirectory does not exist");

            watchManager.stopSavingAllThread();
            return;
        }

        if (!threadSaveManager.enqueueThreadToSave(loadable, posts)) {
            // Probably base directory was removed by the user, can't do anything other than
            // just stop this download
            watchManager.stopSavingThread(loadable);
        }
    }

    @Override
    public void onChanLoaderError(ChanThreadLoader.ChanLoaderException error) {
        Logger.d(TAG, "onChanLoaderError()");
        threadPresenterCallback.showError(error);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onListScrolledToBottom() {
        if (loadable == null) return; //null loadable means no thread loaded, possibly unbinding?
        if (loadable.isThreadMode() && chanLoader != null && chanLoader.getThread() != null
                && chanLoader.getThread().getPostsCount() > 0) {
            List<Post> posts = chanLoader.getThread().getPosts();
            loadable.setLastViewed(posts.get(posts.size() - 1).no);
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }

        threadPresenterCallback.showNewPostsNotification(false, -1);

        // Update the last seen indicator
        showPosts();

        // Update loadable in the database
        databaseManager.runTaskAsync(databaseManager.getDatabaseLoadableManager().updateLoadable(loadable));
    }

    public void onNewPostsViewClicked() {
        if (chanLoader != null) {
            Post post = PostUtils.findPostById(loadable.lastViewed, chanLoader.getThread());
            int position = -1;
            if (post != null) {
                List<Post> posts = threadPresenterCallback.getDisplayingPosts();
                for (int i = 0; i < posts.size(); i++) {
                    Post needle = posts.get(i);
                    if (post.no == needle.no) {
                        position = i;
                        break;
                    }
                }
            }
            //-1 is fine here because we add 1 down the chain to make it 0 if there's no last viewed
            threadPresenterCallback.smoothScrollNewPosts(position);
        }
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        threadPresenterCallback.scrollTo(displayPosition, smooth);
    }

    public void scrollToImage(PostImage postImage, boolean smooth) {
        if (!searchOpen) {
            int position = -1;
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();

            out:
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                if (!post.images.isEmpty()) {
                    for (int j = 0; j < post.images.size(); j++) {
                        if (post.images.get(j) == postImage) {
                            position = i;
                            break out;
                        }
                    }
                }
            }
            if (position >= 0) {
                scrollTo(position, smooth);
            }
        }
    }

    public void scrollToPost(Post needle, boolean smooth) {
        int position = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.no == needle.no) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            scrollTo(position, smooth);
        }
    }

    public void highlightPost(Post post) {
        threadPresenterCallback.highlightPost(post);
    }

    public void selectPost(int post) {
        threadPresenterCallback.selectPost(post);
    }

    public void selectPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            if (!post.images.isEmpty()) {
                for (PostImage image : post.images) {
                    if (image == postImage) {
                        scrollToPost(post, false);
                        highlightPost(post);
                        return;
                    }
                }
            }
        }
    }

    public Post getPostFromPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            if (!post.images.isEmpty()) {
                for (PostImage image : post.images) {
                    if (image == postImage) {
                        return post;
                    }
                }
            }
        }
        return null;
    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (loadable.isCatalogMode()) {
            Loadable newLoadable =
                    Loadable.forThread(loadable.site, post.board, post.no, PostHelper.getTitle(post, loadable));

            Loadable threadLoadable = databaseManager.getDatabaseLoadableManager().get(newLoadable);
            threadPresenterCallback.showThread(threadLoadable);
        }
    }

    @Override
    public void onPostDoubleClicked(Post post) {
        if (!loadable.isCatalogMode()) {
            if (searchOpen) {
                searchQuery = null;
                showPosts();
                threadPresenterCallback.setSearchStatus(null, false, true);
                threadPresenterCallback.showSearch(false);
                highlightPost(post);
                scrollToPost(post, false);
            } else {
                threadPresenterCallback.postClicked(post);
            }
        }
    }

    @Override
    public void onThumbnailClicked(PostImage postImage, ThumbnailView thumbnail) {
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post item : posts) {
            if (!item.images.isEmpty()) {
                for (PostImage image : item.images) {
                    if (!item.deleted.get() || instance(CacheHandler.class).exists(image.imageUrl.toString())) {
                        //deleted posts always have 404'd images, but let it through if the file exists in cache
                        images.add(image);
                        if (image.equalUrl(postImage)) {
                            index = images.size() - 1;
                        }
                    }
                }
            }
        }

        if (chanLoader != null && !images.isEmpty()) {
            threadPresenterCallback.showImages(images, index, chanLoader.getLoadable(), thumbnail);
        }
    }

    @Override
    public Object onPopulatePostOptions(Post post, List<FloatingMenuItem> menu, List<FloatingMenuItem> extraMenu) {
        if (!loadable.isThreadMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_PIN, R.string.action_pin));
        } else if (!loadable.isLocal()) {
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        if (loadable.getSite().feature(Site.Feature.POST_REPORT) && !loadable.isLocal()) {
            menu.add(new FloatingMenuItem(POST_OPTION_REPORT, R.string.post_report));
        }

        if ((loadable.isCatalogMode() || (loadable.isThreadMode() && !post.isOP)) && !loadable.isLocal()) {
            if (!post.filterStub) {
                menu.add(new FloatingMenuItem(POST_OPTION_HIDE, R.string.post_hide));
            }
            menu.add(new FloatingMenuItem(POST_OPTION_REMOVE, R.string.post_remove));
        }

        if (loadable.isThreadMode()) {
            if (!TextUtils.isEmpty(post.id)) {
                menu.add(new FloatingMenuItem(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id));
            }

            if (!TextUtils.isEmpty(post.tripcode)) {
                menu.add(new FloatingMenuItem(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode));
                menu.add(new FloatingMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.post_filter_tripcode));
            }
        }

        if (loadable.site.feature(Site.Feature.POST_DELETE) && databaseManager.getDatabaseSavedReplyManager()
                .isSaved(post.board, post.no) && !loadable.isLocal()) {
            menu.add(new FloatingMenuItem(POST_OPTION_DELETE, R.string.post_delete));
        }

        if (ChanSettings.accessibleInfo.get()) {
            menu.add(new FloatingMenuItem(POST_OPTION_INFO, R.string.post_info));
        } else {
            extraMenu.add(new FloatingMenuItem(POST_OPTION_INFO, R.string.post_info));
        }

        menu.add(new FloatingMenuItem(POST_OPTION_EXTRA, R.string.post_more));

        extraMenu.add(new FloatingMenuItem(POST_OPTION_LINKS, R.string.post_show_links));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_SHARE, R.string.post_share));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text));

        if (!loadable.isLocal()) {
            boolean isSaved = databaseManager.getDatabaseSavedReplyManager().isSaved(post.board, post.no);
            extraMenu.add(new FloatingMenuItem(POST_OPTION_SAVE, isSaved ? R.string.unsave : R.string.save));
        }

        return POST_OPTION_EXTRA;
    }

    public void onPostOptionClicked(Post post, Object id) {
        switch ((Integer) id) {
            case POST_OPTION_QUOTE:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, false);
                break;
            case POST_OPTION_QUOTE_TEXT:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, true);
                break;
            case POST_OPTION_INFO:
                showPostInfo(post);
                break;
            case POST_OPTION_LINKS:
                if (post.linkables.size() > 0) {
                    threadPresenterCallback.showPostLinkables(post);
                }
                break;
            case POST_OPTION_COPY_TEXT:
                threadPresenterCallback.clipboardPost(post);
                break;
            case POST_OPTION_REPORT:
                threadPresenterCallback.openReportView(post);
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_HIGHLIGHT_TRIPCODE:
                threadPresenterCallback.highlightPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER_TRIPCODE:
                threadPresenterCallback.filterPostTripcode(post.tripcode);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                SavedReply savedReply = SavedReply.fromSiteBoardNoPassword(post.board.site, post.board, post.no, "");
                if (databaseManager.getDatabaseSavedReplyManager().isSaved(post.board, post.no)) {
                    databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().unsaveReply(savedReply));
                    Pin watchedPin = watchManager.getPinByLoadable(loadable);
                    if (watchedPin != null) {
                        synchronized (this) {
                            watchedPin.quoteLastCount -= post.repliesFrom.size();
                        }
                    }
                } else {
                    databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().saveReply(savedReply));
                    Pin watchedPin = watchManager.getPinByLoadable(loadable);
                    if (watchedPin != null) {
                        synchronized (this) {
                            watchedPin.quoteLastCount += post.repliesFrom.size();
                        }
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_PIN:
                String title = PostHelper.getTitle(post, loadable);
                Loadable pinLoadable = databaseManager.getDatabaseLoadableManager()
                        .get(Loadable.forThread(loadable.site, post.board, post.no, title));
                watchManager.createPin(pinLoadable, post, PinType.WATCH_NEW_POSTS);
                break;
            case POST_OPTION_OPEN_BROWSER:
                openLink(loadable.site.resolvable().desktopUrl(loadable, post));
                break;
            case POST_OPTION_SHARE:
                shareLink(loadable.site.resolvable().desktopUrl(loadable, post));
                break;
            case POST_OPTION_REMOVE:
            case POST_OPTION_HIDE:
                if (chanLoader == null || chanLoader.getThread() == null) {
                    break;
                }

                boolean hide = ((Integer) id) == POST_OPTION_HIDE;

                if (chanLoader.getThread().getLoadable().mode == Loadable.Mode.CATALOG) {
                    threadPresenterCallback.hideThread(post, post.no, hide);
                } else {
                    boolean isEmpty = false;

                    synchronized (post.repliesFrom) {
                        isEmpty = post.repliesFrom.isEmpty();
                    }

                    if (isEmpty) {
                        // no replies to this post so no point in showing the dialog
                        hideOrRemovePosts(hide, false, post, chanLoader.getThread().getOp().no);
                    } else {
                        // show a dialog to the user with options to hide/remove the whole chain of posts
                        threadPresenterCallback.showHideOrRemoveWholeChainDialog(hide,
                                post,
                                chanLoader.getThread().getOp().no
                        );
                    }
                }
                break;
        }
    }

    @Override
    public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE && chanLoader != null) {
            Post linked = PostUtils.findPostById((int) linkable.value, chanLoader.getThread());
            if (linked != null) {
                threadPresenterCallback.showPostsPopup(post, Collections.singletonList(linked));
            }
        } else if (linkable.type == PostLinkable.Type.LINK) {
            threadPresenterCallback.openLink((String) linkable.value);
        } else if (linkable.type == PostLinkable.Type.THREAD) {
            CommentParser.ThreadLink link = (CommentParser.ThreadLink) linkable.value;

            Board board = loadable.site.board(link.board);
            if (board != null) {
                Loadable thread = databaseManager.getDatabaseLoadableManager()
                        .get(Loadable.forThread(board.site, board, link.threadId, ""));
                thread.markedNo = link.postId;

                threadPresenterCallback.showThread(thread);
            }
        } else if (linkable.type == PostLinkable.Type.BOARD) {
            Board board = databaseManager.runTask(databaseManager.getDatabaseBoardManager()
                    .getBoard(loadable.site, (String) linkable.value));
            Loadable catalog = databaseManager.getDatabaseLoadableManager().get(Loadable.forCatalog(board));

            threadPresenterCallback.showBoard(catalog);
        } else if (linkable.type == PostLinkable.Type.SEARCH) {
            CommentParser.SearchLink search = (CommentParser.SearchLink) linkable.value;
            Board board = databaseManager.runTask(databaseManager.getDatabaseBoardManager()
                    .getBoard(loadable.site, search.board));
            Loadable catalog = databaseManager.getDatabaseLoadableManager().get(Loadable.forCatalog(board));

            threadPresenterCallback.showBoardAndSearch(catalog, search.search);
        }
    }

    @Override
    public void onPostNoClicked(Post post) {
        threadPresenterCallback.quote(post, false);
    }

    @Override
    public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        threadPresenterCallback.quote(post, quoted);
    }

    @Override
    public void onShowPostReplies(Post post) {
        List<Post> posts = new ArrayList<>();
        synchronized (post.repliesFrom) {
            for (int no : post.repliesFrom) {
                if (chanLoader != null) {
                    Post replyPost = PostUtils.findPostById(no, chanLoader.getThread());
                    if (replyPost != null) {
                        posts.add(replyPost);
                    }
                }
            }
        }
        if (posts.size() > 0) {
            threadPresenterCallback.showPostsPopup(post, posts);
        }
    }

    /*
     * ThreadStatusCell callbacks
     */
    @Override
    public long getTimeUntilLoadMore() {
        if (chanLoader != null) {
            return chanLoader.getTimeUntilLoadMore();
        } else {
            return 0L;
        }
    }

    @Override
    public boolean isWatching() {
        //@formatter:off
        return loadable.isThreadMode()
            && ChanSettings.autoRefreshThread.get()
            && BackgroundUtils.isInForeground()
            && chanLoader != null
            && chanLoader.getThread() != null
            && !chanLoader.getThread().isClosed()
            && !chanLoader.getThread().isArchived();
        //@formatter:on
    }

    @Nullable
    @Override
    public ChanThread getChanThread() {
        return chanLoader == null ? null : chanLoader.getThread();
    }

    public Chan4PagesRequest.Page getPage(Post op) {
        return pageRequestManager.getPage(op);
    }

    @Override
    public void onListStatusClicked() {
        if (getChanThread() != null && !getChanThread().isArchived()) {
            chanLoader.requestMoreDataAndResetTimer();
        } else {
            @SuppressLint("InflateParams")
            final ArchivesLayout dialogView = (ArchivesLayout) inflate(context, R.layout.layout_archives, null);
            dialogView.setBoard(loadable.board);
            dialogView.setCallback(this);

            AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView)
                    .setTitle(R.string.thread_show_archives)
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
        }
    }

    @Override
    public void showThread(Loadable loadable) {
        threadPresenterCallback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        if (chanLoader != null && loadable != null && loadable.isThreadMode()) {
            chanLoader.requestMoreDataAndResetTimer();
            //put in a "request" for a page update whenever the next set of data comes in
            forcePageUpdate = true;
        }
    }

    @Override
    public void onUnhidePostClick(Post post) {
        threadPresenterCallback.unhideOrUnremovePost(post);
    }

    public void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager()
                .findSavedReply(post.board, post.no));
        if (reply != null) {
            Site site = loadable.getSite();
            site.actions().delete(new DeleteRequest(post, reply, onlyImageDelete), new SiteActions.DeleteListener() {
                @Override
                public void onDeleteComplete(HttpCall httpPost, DeleteResponse deleteResponse) {
                    String message;
                    if (deleteResponse.deleted) {
                        message = getString(R.string.delete_success);
                    } else if (!TextUtils.isEmpty(deleteResponse.errorMessage)) {
                        message = deleteResponse.errorMessage;
                    } else {
                        message = getString(R.string.delete_error);
                    }
                    threadPresenterCallback.hideDeleting(message);
                }

                @Override
                public void onDeleteError(HttpCall httpCall) {
                    threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                }
            });
        }
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager()
                .findSavedReply(post.board, post.no));
        if (reply != null) {
            threadPresenterCallback.confirmPostDelete(post);
        }
    }

    private void showPostInfo(Post post) {
        StringBuilder text = new StringBuilder();

        for (PostImage image : post.images) {
            text.append("Filename: ").append(image.filename).append(".").append(image.extension);
            if (image.size == -1) {
                text.append("\nLinked file");
            } else {
                text.append(" \nDimensions: ")
                        .append(image.imageWidth)
                        .append("x")
                        .append(image.imageHeight)
                        .append("\nSize: ")
                        .append(getReadableFileSize(image.size));
            }

            if (image.spoiler && image.size != -1) { //all linked files are spoilered, don't say that
                text.append("\nSpoilered");
            }

            text.append("\n");
        }

        text.append("Posted: ").append(PostHelper.getLocalDate(post));

        if (!TextUtils.isEmpty(post.id)) {
            text.append("\nId: ").append(post.id);
            int count = 0;
            try {
                for (Post p : chanLoader.getThread().getPosts()) {
                    if (p.id.equals(post.id)) count++;
                }
            } catch (Exception ignored) {
            }
            text.append("\nCount: ").append(count);
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text.append("\nTripcode: ").append(post.tripcode);
        }

        if (post.httpIcons != null && !post.httpIcons.isEmpty()) {
            for (PostHttpIcon icon : post.httpIcons) {
                if (icon.url.toString().contains("troll")) {
                    text.append("\nTroll Country: ").append(icon.name);
                } else if (icon.url.toString().contains("country")) {
                    text.append("\nCountry: ").append(icon.name);
                } else if (icon.url.toString().contains("minileaf")) {
                    text.append("\n4chan Pass Year: ").append(icon.name);
                }
            }
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            text.append("\nCapcode: ").append(post.capcode);
        }

        threadPresenterCallback.showPostInfo(text.toString());
    }

    private void showPosts() {
        showPosts(false);
    }

    private void showPosts(boolean refreshAfterHideOrRemovePosts) {
        if (chanLoader != null && chanLoader.getThread() != null) {
            threadPresenterCallback.showPosts(
                    chanLoader.getThread(),
                    new PostsFilter(order, searchQuery),
                    refreshAfterHideOrRemovePosts
            );
        }
    }

    private void addHistory() {
        if (chanLoader == null || chanLoader.getThread() == null) {
            return;
        }

        if (!historyAdded && addToLocalBackHistory && ChanSettings.historyEnabled.get() && loadable.isThreadMode()
                // Do not attempt to add a saved thread to the history
                && !loadable.isLocal()) {
            historyAdded = true;
            History history = new History();
            history.loadable = loadable;
            PostImage image = chanLoader.getThread().getOp().image();
            history.thumbnailUrl = image == null ? "" : image.getThumbnailUrl().toString();
            databaseManager.runTaskAsync(databaseManager.getDatabaseHistoryManager().addHistory(history));
        }
    }

    public void showImageReencodingWindow(boolean supportsReencode) {
        threadPresenterCallback.showImageReencodingWindow(loadable, supportsReencode);
    }

    public void hideOrRemovePosts(boolean hide, boolean wholeChain, Post post, int threadNo) {
        Set<Post> posts = new HashSet<>();

        if (chanLoader != null) {
            if (wholeChain) {
                ChanThread thread = chanLoader.getThread();
                if (thread != null) {
                    posts.addAll(PostUtils.findPostWithReplies(post.no, thread.getPosts()));
                }
            } else {
                posts.add(PostUtils.findPostById(post.no, chanLoader.getThread()));
            }
        }

        threadPresenterCallback.hideOrRemovePosts(hide, wholeChain, posts, threadNo);
    }

    public void showRemovedPostsDialog() {
        if (chanLoader == null || chanLoader.getThread() == null
                || chanLoader.getThread().getLoadable().mode != Loadable.Mode.THREAD) {
            return;
        }

        threadPresenterCallback.viewRemovedPostsForTheThread(chanLoader.getThread().getPosts(),
                chanLoader.getThread().getOp().no
        );
    }

    public void onRestoreRemovedPostsClicked(List<Integer> selectedPosts) {
        if (chanLoader == null || chanLoader.getThread() == null) {
            return;
        }

        int threadNo = chanLoader.getThread().getOp().no;
        Site site = chanLoader.getThread().getLoadable().site;
        String boardCode = chanLoader.getThread().getLoadable().boardCode;

        threadPresenterCallback.onRestoreRemovedPostsClicked(threadNo, site, boardCode, selectedPosts);
    }

    @Override
    public void openArchive(Pair<String, String> domainNamePair) {
        Post tempOP = new Post.Builder().board(loadable.board)
                .id(loadable.no)
                .opId(loadable.no)
                .setUnixTimestampSeconds(1)
                .comment("")
                .build();
        String link = loadable.site.resolvable().desktopUrl(loadable, tempOP);
        link = link.replace("https://boards.4chan.org/", "https://" + domainNamePair.second + "/");
        openLinkInBrowser((Activity) context, link);
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void updateLoadable(Loadable.LoadableDownloadingState loadableDownloadingState) {
        loadable.loadableDownloadingState = loadableDownloadingState;
    }

    public void markAllPostsAsSeen() {
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            SavedThread savedThread = null;

            if (PinType.hasDownloadFlag(pin.pinType)) {
                savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
            }

            if (savedThread == null) {
                watchManager.onBottomPostViewed(pin);
            }
        }
    }

    public interface ThreadPresenterCallback {
        void showPosts(ChanThread thread, PostsFilter filter, boolean refreshAfterHideOrRemovePosts);

        void postClicked(Post post);

        void showError(ChanThreadLoader.ChanLoaderException error);

        void showLoading();

        void showEmpty();

        void showPostInfo(String info);

        void showPostLinkables(Post post);

        void clipboardPost(Post post);

        void showThread(Loadable threadLoadable);

        void showBoard(Loadable catalogLoadable);

        void showBoardAndSearch(Loadable catalogLoadable, String searchQuery);

        void openLink(String link);

        void openReportView(Post post);

        void showPostsPopup(Post forPost, List<Post> posts);

        void hidePostsPopup();

        List<Post> getDisplayingPosts();

        int[] getCurrentPosition();

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void showAlbum(List<PostImage> images, int index);

        void scrollTo(int displayPosition, boolean smooth);

        void smoothScrollNewPosts(int displayPosition);

        void highlightPost(Post post);

        void highlightPostId(String id);

        void highlightPostTripcode(String tripcode);

        void filterPostTripcode(String tripcode);

        void selectPost(int post);

        void showSearch(boolean show);

        void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void quote(Post post, CharSequence text);

        void confirmPostDelete(Post post);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post, int threadNo, boolean hide);

        void showNewPostsNotification(boolean show, int more);

        void showImageReencodingWindow(Loadable loadable, boolean supportsReencode);

        void showHideOrRemoveWholeChainDialog(boolean hide, Post post, int threadNo);

        void hideOrRemovePosts(boolean hide, boolean wholeChain, Set<Post> posts, int threadNo);

        void unhideOrUnremovePost(Post post);

        void viewRemovedPostsForTheThread(List<Post> threadPosts, int threadNo);

        void onRestoreRemovedPostsClicked(int threadNo, Site site, String boardCode, List<Integer> selectedPosts);
    }
}
