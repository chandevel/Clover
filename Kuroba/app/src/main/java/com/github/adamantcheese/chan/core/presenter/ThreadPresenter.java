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
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
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
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.LayoutUtils;
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
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class ThreadPresenter
        implements ChanThreadLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback,
                   PostCellInterface.PostCellCallback, ThreadStatusCell.Callback,
                   ThreadListLayout.ThreadListLayoutPresenterCallback, ArchivesLayout.Callback {
    //region Private Variables
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
    private static final int POST_OPTION_FILTER = 14;
    private static final int POST_OPTION_FILTER_TRIPCODE = 15;
    private static final int POST_OPTION_FILTER_IMAGE_HASH = 16;
    private static final int POST_OPTION_FILTER_SUBJECT = 17;
    private static final int POST_OPTION_FILTER_COMMENT = 18;
    private static final int POST_OPTION_FILTER_NAME = 19;
    private static final int POST_OPTION_FILTER_ID = 20;
    private static final int POST_OPTION_FILTER_FILENAME = 21;
    private static final int POST_OPTION_FILTER_COUNTRY_CODE = 22;
    private static final int POST_OPTION_EXTRA = 23;
    private static final int POST_OPTION_REMOVE = 24;
    private static final int POST_OPTION_MOCK_REPLY = 25;

    private final WatchManager watchManager;
    private final DatabaseManager databaseManager;
    private final ChanLoaderManager chanLoaderManager;
    private final PageRequestManager pageRequestManager;
    private final ThreadSaveManager threadSaveManager;
    private final FileManager fileManager;
    private final MockReplyManager mockReplyManager;

    private ThreadPresenterCallback threadPresenterCallback;
    private Loadable loadable;
    private ChanThreadLoader chanLoader;
    private boolean searchOpen;
    private String searchQuery;
    private boolean forcePageUpdate;
    private PostsFilter.Order order = PostsFilter.Order.BUMP;
    private boolean historyAdded;
    private boolean addToLocalBackHistory;
    private Context context;
    private List<FloatingMenuItem> filterMenu;
    //endregion

    @Inject
    public ThreadPresenter(
            WatchManager watchManager,
            DatabaseManager databaseManager,
            ChanLoaderManager chanLoaderManager,
            PageRequestManager pageRequestManager,
            ThreadSaveManager threadSaveManager,
            FileManager fileManager,
            MockReplyManager mockReplyManager
    ) {
        this.watchManager = watchManager;
        this.databaseManager = databaseManager;
        this.chanLoaderManager = chanLoaderManager;
        this.pageRequestManager = pageRequestManager;
        this.threadSaveManager = threadSaveManager;
        this.fileManager = fileManager;
        this.mockReplyManager = mockReplyManager;
    }

    public void create(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;
    }

    public void showNoContent() {
        threadPresenterCallback.showEmpty();
    }

    public synchronized void bindLoadable(Loadable loadable, boolean addToLocalBackHistory) {
        if (!loadable.equals(this.loadable)) {
            if (isBound()) {
                stopSavingThreadIfItIsBeingSaved(this.loadable);
                unbindLoadable();
            }

            this.loadable = loadable;
            this.addToLocalBackHistory = addToLocalBackHistory;

            startSavingThreadIfItIsNotBeingSaved(this.loadable);
            chanLoader = chanLoaderManager.obtain(loadable, this);
            loadable.site.actions().archives(Chan.instance(ArchivesManager.class));
            threadPresenterCallback.showLoading();
        }
    }

    public synchronized void bindLoadable(Loadable loadable) {
        bindLoadable(loadable, true);
    }

    public synchronized void unbindLoadable() {
        if (isBound()) {
            chanLoader.clearTimer();
            chanLoaderManager.release(chanLoader, this);
            chanLoader = null;
            loadable = null;
            historyAdded = false;
            addToLocalBackHistory = true;

            threadPresenterCallback.showLoading();
        }
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
                || loadable.getLoadableDownloadingState() == Loadable.LoadableDownloadingState.AlreadyDownloaded
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
        if (loadable.getLoadableDownloadingState() == Loadable.LoadableDownloadingState.AlreadyDownloaded
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
        return loadable != null && chanLoader != null;
    }

    public void requestInitialData() {
        if (isBound()) {
            if (chanLoader.getThread() == null) {
                requestData();
            } else {
                chanLoader.quickLoad();
            }
        }
    }

    public void requestData() {
        BackgroundUtils.ensureMainThread();

        if (isBound()) {
            threadPresenterCallback.showLoading();
            chanLoader.requestData();
        }
    }

    public void onForegroundChanged(boolean foreground) {
        if (isBound()) {
            if (foreground && isWatching()) {
                chanLoader.requestMoreData();
                if (chanLoader.getThread() != null) {
                    // Show loading indicator in the status cell
                    showPosts();
                }
            } else {
                chanLoader.clearTimer();
            }
        }
    }

    public synchronized boolean pin() {
        if (!isBound()) return false;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) {
            if (chanLoader.getThread() != null) {
                Post op = chanLoader.getThread().getOp();
                watchManager.createPin(loadable, op, PinType.WATCH_NEW_POSTS);
            } else {
                watchManager.createPin(loadable);
            }
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

    public synchronized boolean save() {
        if (!isBound()) return false;
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

        loadable.setLoadableState(Loadable.LoadableDownloadingState.NotDownloading);
        return true;
    }

    private boolean saveInternal() {
        if (chanLoader.getThread() == null) {
            Logger.e(this, "chanLoader.getThread() == null");
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
            showToast(context, R.string.thread_layout_background_watcher_is_disabled_message, Toast.LENGTH_LONG);
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
        if (!isBound()) return false;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null) return false;
        return PinType.hasWatchNewPostsFlag(pin.pinType);
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
        if (!visible) {
            searchQuery = null;
        }

        if (chanLoader != null && chanLoader.getThread() != null && !visible) {
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
        showPosts(true);
    }

    public synchronized void showAlbum() {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        int[] pos = threadPresenterCallback.getCurrentPosition();
        int displayPosition = pos[0];

        List<PostImage> images = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            images.addAll(item.images);
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

        if (isBound()) {
            if (isWatching()) {
                chanLoader.setTimer();
            }
        } else {
            Logger.e(this, "onChanLoaderData when not bound!");
            return;
        }

        loadable.setLoadableState(result.getLoadable().getLoadableDownloadingState());
        Logger.d(this, "onChanLoaderData() downloadingState = " + loadable.getLoadableDownloadingState().name());

        //allow for search refreshes inside the catalog
        if (result.getLoadable().isCatalogMode() && !TextUtils.isEmpty(searchQuery)) {
            onSearchEntered(searchQuery);
        } else {
            showPosts();
        }

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
            if (loadable.lastViewed == -1) {
                loadable.setLastViewed(loadable.lastLoaded);
            }

            if (more > 0 && loadable.no == result.getLoadable().no) {
                threadPresenterCallback.showNewPostsNotification(true, more);
                //deal with any "requests" for a page update
                if (forcePageUpdate) {
                    pageRequestManager.forceUpdateForBoard(loadable.board);
                    forcePageUpdate = false;
                }
            }
        }

        if (loadable.markedNo >= 0) {
            Post markedPost = PostUtils.findPostById(loadable.markedNo, chanLoader.getThread());
            if (markedPost != null) {
                highlightPost(markedPost);
                if (BackgroundUtils.isInForeground()) {
                    scrollToPost(markedPost, false);
                }
                if (StartActivity.loadedFromURL) {
                    BackgroundUtils.runOnMainThread(() -> scrollToPost(markedPost, false), 1000);
                    StartActivity.loadedFromURL = false;
                }
            }
            loadable.markedNo = -1;
        }

        storeNewPostsIfThreadIsBeingDownloaded(result.getPosts());
        addHistory();

        // Update loadable in the database
        databaseManager.runTaskAsync(databaseManager.getDatabaseLoadableManager().updateLoadable(loadable));

        if (!ChanSettings.watchEnabled.get() && !ChanSettings.watchBackground.get()
                && loadable.getLoadableDownloadingState() == Loadable.LoadableDownloadingState.AlreadyDownloaded) {
            Pin pin = watchManager.findPinByLoadableId(loadable.id);
            if (pin != null) {
                pin.isError = true;
                pin.watching = false;

                watchManager.updatePin(pin, true);
            } else {
                Logger.d(this, "Could not find pin with loadableId = " + loadable.id + ", it was already deleted?");
            }
        }

        BackgroundUtils.runOnBackgroundThread(() -> instance(FilterWatchManager.class).onCatalogLoad(result));
    }

    private void storeNewPostsIfThreadIsBeingDownloaded(List<Post> posts) {
        if (posts.isEmpty() || loadable.isCatalogMode()
                || loadable.getLoadableDownloadingState() == Loadable.LoadableDownloadingState.AlreadyDownloaded) {
            return;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
            // No pin for this loadable we are probably not downloading this thread
            // or no downloading flag
            return;
        }

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
        if (savedThread == null || savedThread.isStopped || savedThread.isFullyDownloaded) {
            // Either the thread is not being downloaded or it is stopped or already fully downloaded
            return;
        }

        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
            Logger.d(this, "storeNewPostsIfThreadIsBeingDownloaded() LocalThreadsBaseDirectory does not exist");

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
        Logger.d(this, "onChanLoaderError()");
        threadPresenterCallback.showError(error);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onListScrolledToBottom() {
        if (!isBound()) return;
        if (chanLoader.getThread() != null && loadable.isThreadMode() && chanLoader.getThread().getPostsCount() > 0) {
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
        if (!isBound()) return;
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
                for (int j = 0; j < post.images.size(); j++) {
                    if (post.images.get(j) == postImage) {
                        position = i;
                        break out;
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
            for (PostImage image : post.images) {
                if (image == postImage) {
                    scrollToPost(post, false);
                    highlightPost(post);
                    return;
                }
            }
        }
    }

    public Post getPostFromPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            for (PostImage image : post.images) {
                if (image == postImage) {
                    return post;
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
        if (isBound() && loadable.isCatalogMode()) {
            highlightPost(post);
            threadPresenterCallback.showThread(Loadable.forThread(post.board,
                    post.no,
                    PostHelper.getTitle(post, loadable)
            ));
        }
    }

    @Override
    public void onPostDoubleClicked(Post post) {
        if (isBound() && loadable.isThreadMode()) {
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
        if (!isBound()) return;
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post item : posts) {
            for (PostImage image : item.images) {
                if (!item.deleted.get() || instance(CacheHandler.class).exists(image.imageUrl)) {
                    //deleted posts always have 404'd images, but let it through if the file exists in cache
                    images.add(image);
                    if (image.equalUrl(postImage)) {
                        index = images.size() - 1;
                    }
                }
            }
        }

        if (!images.isEmpty()) {
            threadPresenterCallback.showImages(images, index, loadable, thumbnail);
        }
    }

    @Override
    public Object onPopulatePostOptions(Post post, List<FloatingMenuItem> menu, List<FloatingMenuItem> extraMenu) {
        if (!isBound()) return null;
        if (loadable.isCatalogMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_PIN, R.string.action_pin));
        } else if (!loadable.isLocal()) {
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POST_REPORT) && !loadable.isLocal()) {
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
            }
        }

        filterMenu = new ArrayList<>();
        if (post.isOP && !TextUtils.isEmpty(post.subject)) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_SUBJECT, R.string.filter_subject));
        }
        if (!TextUtils.isEmpty(post.comment)) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_COMMENT, R.string.filter_comment));
        }
        if (!TextUtils.isEmpty(post.name) && !TextUtils.equals(post.name, "Anonymous")) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_NAME, R.string.filter_name));
        }
        if (!TextUtils.isEmpty(post.id)) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_ID, R.string.filter_id));
        }
        if (!TextUtils.isEmpty(post.tripcode)) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.filter_tripcode));
        }
        if (loadable.board.countryFlags) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_COUNTRY_CODE, R.string.filter_country_code));
        }
        if (!post.images.isEmpty()) {
            filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_FILENAME, R.string.filter_filename));
            if (loadable.site.siteFeature(Site.SiteFeature.IMAGE_FILE_HASH)) {
                filterMenu.add(new FloatingMenuItem(POST_OPTION_FILTER_IMAGE_HASH, R.string.filter_image_hash));
            }
        }

        //if the filter menu only has a single option we place just that option in the root menu
        //in some cases a post will have nothing in it to filter (for example a post with no text and an image
        //that is removed by a filter), in such cases there is no filter menu option.
        if (filterMenu.size() > 1) {
            menu.add(new FloatingMenuItem(POST_OPTION_FILTER, R.string.post_filter));
        } else if (filterMenu.size() == 1) {
            FloatingMenuItem menuItem = filterMenu.remove(0);
            menu.add(new FloatingMenuItem(menuItem.getId(), "Filter " + menuItem.getText().toLowerCase()));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POST_DELETE) && databaseManager.getDatabaseSavedReplyManager()
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
            extraMenu.add(new FloatingMenuItem(POST_OPTION_SAVE,
                    isSaved ? R.string.unmark_as_my_post : R.string.mark_as_my_post
            ));

            if (BuildConfig.DEV_BUILD && loadable.no > 0) {
                extraMenu.add(new FloatingMenuItem(POST_OPTION_MOCK_REPLY, R.string.mock_reply));
            }
        }

        return POST_OPTION_EXTRA;
    }

    public void onPostOptionClicked(View anchor, Post post, Object id, boolean inPopup) {
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
                    List<CharSequence> keys = new ArrayList<>();
                    Bitmap youtubeIcon = instance(BitmapRepository.class).youtubeIcon;
                    for (int i = 0; i < post.linkables.size(); i++) {
                        //skip SPOILER linkables, they aren't useful to display
                        if (post.linkables.get(i).type == PostLinkable.Type.SPOILER) continue;
                        String key = post.linkables.get(i).key.toString();
                        String value = post.linkables.get(i).value.toString();
                        if (value.contains("youtu.be") || value.contains("youtube")) {
                            //need to trim off starting spaces for youtube links
                            keys.add(PostHelper.prependIcon(context, key.substring(2), youtubeIcon, sp(16)));
                        } else {
                            keys.add(key);
                        }
                    }

                    AlertDialog dialog = new AlertDialog.Builder(context).create();

                    ListView clickables = new ListView(context);
                    clickables.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, keys));
                    clickables.setOnItemClickListener((parent, view, position, id1) -> {
                        onPostLinkableClicked(post, post.linkables.get(position));
                        dialog.dismiss();
                    });
                    clickables.setOnItemLongClickListener((parent, view, position, id1) -> {
                        setClipboardContent("Linkable URL", post.linkables.get(position).value.toString());
                        showToast(context, R.string.linkable_copied_to_clipboard);
                        return false;
                    });

                    dialog.setView(clickables);
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.show();
                }
                break;
            case POST_OPTION_COPY_TEXT:
                setClipboardContent("Post text", post.comment.toString());
                showToast(context, R.string.post_text_copied);
                break;
            case POST_OPTION_REPORT:
                if (inPopup) {
                    threadPresenterCallback.hidePostsPopup();
                }
                threadPresenterCallback.openReportView(post);
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_HIGHLIGHT_TRIPCODE:
                threadPresenterCallback.highlightPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER:
                showFilterOptions(anchor, post, inPopup, filterMenu);
                break;
            case POST_OPTION_FILTER_SUBJECT:
                threadPresenterCallback.filterPostSubject(post.subject);
                break;
            case POST_OPTION_FILTER_COMMENT:
                threadPresenterCallback.filterPostComment(post.comment);
                break;
            case POST_OPTION_FILTER_NAME:
                threadPresenterCallback.filterPostName(post.name);
                break;
            case POST_OPTION_FILTER_FILENAME:
                threadPresenterCallback.filterPostFilename(post);
                break;
            case POST_OPTION_FILTER_COUNTRY_CODE:
                threadPresenterCallback.filterPostCountryCode(post);
                break;
            case POST_OPTION_FILTER_ID:
                threadPresenterCallback.filterPostID(post.id);
                break;
            case POST_OPTION_FILTER_TRIPCODE:
                threadPresenterCallback.filterPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER_IMAGE_HASH:
                threadPresenterCallback.filterPostImageHash(post);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                if (!isBound()) break;
                Set<Post> matchingPosts = new HashSet<>(1);
                if (TextUtils.isEmpty(post.id)) {
                    //just this post
                    matchingPosts.add(post);
                } else {
                    //match all post IDs
                    //noinspection ConstantConditions, isBound check takes care of this
                    for (Post p : getChanThread().getPosts()) {
                        if (!TextUtils.isEmpty(p.id) && p.id.equals(post.id)) {
                            matchingPosts.add(p);
                        }
                    }
                }

                if (databaseManager.getDatabaseSavedReplyManager().isSaved(post.board, post.no)) {
                    //unsave all matching posts
                    for (Post p : matchingPosts) {
                        SavedReply saved = SavedReply.fromBoardNoPassword(p.board, p.no, "");
                        databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().unsaveReply(saved));
                        Pin watchedPin = watchManager.getPinByLoadable(loadable);
                        if (watchedPin != null) {
                            synchronized (this) {
                                watchedPin.quoteLastCount -= p.repliesFrom.size();
                            }
                        }
                    }
                } else {
                    //save all matching posts
                    for (Post p : matchingPosts) {
                        SavedReply saved = SavedReply.fromBoardNoPassword(p.board, p.no, "");
                        databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().saveReply(saved));
                        Pin watchedPin = watchManager.getPinByLoadable(loadable);
                        if (watchedPin != null) {
                            synchronized (this) {
                                watchedPin.quoteLastCount += p.repliesFrom.size();
                            }
                        }
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_PIN:
                watchManager.createPin(Loadable.forThread(post.board, post.no, PostHelper.getTitle(post, loadable)),
                        post,
                        PinType.WATCH_NEW_POSTS
                );
                break;
            case POST_OPTION_OPEN_BROWSER:
                if (isBound()) {
                    openLink(loadable.site.resolvable().desktopUrl(loadable, post.no));
                }
                break;
            case POST_OPTION_SHARE:
                if (isBound()) {
                    shareLink(loadable.site.resolvable().desktopUrl(loadable, post.no));
                }
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
                    boolean isEmpty;

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
            case POST_OPTION_MOCK_REPLY:
                if (isBound()) {
                    mockReplyManager.addMockReply(post.board.siteId, loadable.boardCode, loadable.no, post.no);
                    showToast(context, "Refresh to add mock replies");
                }
                break;
        }
    }

    private void showFilterOptions(View anchor, Post post, Boolean inPopup, List<FloatingMenuItem> options) {
        FloatingMenu menu = new FloatingMenu(context, anchor, options);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                onPostOptionClicked(anchor, post, item.getId(), inPopup);
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    @Override
    public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE && isBound()) {
            Post linked = PostUtils.findPostById((int) linkable.value, chanLoader.getThread());
            if (linked != null) {
                threadPresenterCallback.showPostsPopup(post, Collections.singletonList(linked));
            }
        } else if (linkable.type == PostLinkable.Type.LINK) {
            threadPresenterCallback.openLink((String) linkable.value);
        } else if (linkable.type == PostLinkable.Type.THREAD && isBound()) {
            CommentParser.ThreadLink link = (CommentParser.ThreadLink) linkable.value;

            Board board = loadable.site.board(link.board);
            if (board != null) {
                Loadable thread = Loadable.forThread(board, link.threadId, "");
                thread.markedNo = link.postId;

                threadPresenterCallback.showThread(thread);
            }
        } else if (linkable.type == PostLinkable.Type.BOARD && isBound()) {
            Board board = databaseManager.runTask(databaseManager.getDatabaseBoardManager()
                    .getBoard(loadable.site, (String) linkable.value));
            if (board == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoard(Loadable.forCatalog(board));
            }
        } else if (linkable.type == PostLinkable.Type.SEARCH && isBound()) {
            CommentParser.SearchLink search = (CommentParser.SearchLink) linkable.value;
            Board board = databaseManager.runTask(databaseManager.getDatabaseBoardManager()
                    .getBoard(loadable.site, search.board));
            if (board == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoardAndSearch(Loadable.forCatalog(board), search.search);
            }
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
        if (!isBound()) return;
        List<Post> posts = new ArrayList<>();
        synchronized (post.repliesFrom) {
            for (int no : post.repliesFrom) {
                Post replyPost = PostUtils.findPostById(no, chanLoader.getThread());
                if (replyPost != null) {
                    posts.add(replyPost);
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
        if (isBound()) {
            return chanLoader.getTimeUntilLoadMore();
        } else {
            return 0L;
        }
    }

    @Override
    public boolean isWatching() {
        //@formatter:off
        return ChanSettings.autoRefreshThread.get()
            && BackgroundUtils.isInForeground()
            && isBound()
            && loadable.isThreadMode()
            && chanLoader.getThread() != null
            && !chanLoader.getThread().isClosed()
            && !chanLoader.getThread().isArchived();
        //@formatter:on
    }

    @Nullable
    @Override
    public ChanThread getChanThread() {
        return isBound() ? chanLoader.getThread() : null;
    }

    public ChanPage getPage(Post op) {
        return pageRequestManager.getPage(op);
    }

    @Override
    public void onListStatusClicked() {
        if (!isBound()) return;
        //noinspection ConstantConditions
        if (!chanLoader.getThread().isArchived()) {
            chanLoader.requestMoreData();
        } else {
            @SuppressLint("InflateParams")
            final ArchivesLayout dialogView = (ArchivesLayout) inflate(context, R.layout.layout_archives, null);
            boolean hasContents = dialogView.setBoard(loadable.board);
            dialogView.setCallback(this);

            if (hasContents) {
                AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogView)
                        .setTitle(R.string.thread_show_archives)
                        .create();
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            } else {
                showToast(context, "No archives for this board or site.");
            }
        }
    }

    @Override
    public void showThread(Loadable loadable) {
        threadPresenterCallback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        if (isBound() && loadable.isThreadMode()) {
            chanLoader.requestMoreData();
            //put in a "request" for a page update whenever the next set of data comes in
            forcePageUpdate = true;
        }
    }

    @Override
    public void onUnhidePostClick(Post post) {
        threadPresenterCallback.unhideOrUnremovePost(post);
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseManager.getDatabaseSavedReplyManager().getSavedReply(post.board, post.no);
        if (reply != null) {
            @SuppressLint("InflateParams")
            final View view = LayoutUtils.inflate(context, R.layout.dialog_post_delete, null);
            CheckBox checkBox = view.findViewById(R.id.image_only);
            new AlertDialog.Builder(context).setTitle(R.string.delete_confirm)
                    .setView(view)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete,
                            (dialog, which) -> deletePostConfirmed(post, checkBox.isChecked())
                    )
                    .show();
        }
    }

    private void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseManager.getDatabaseSavedReplyManager().getSavedReply(post.board, post.no);
        if (reply != null) {
            post.board.site.actions()
                    .delete(new DeleteRequest(post, reply, onlyImageDelete), new SiteActions.DeleteListener() {
                        @Override
                        public void onDeleteComplete(DeleteResponse deleteResponse) {
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
                        public void onDeleteError(Exception e) {
                            threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                        }
                    });
        }
    }

    private void showPostInfo(Post post) {
        StringBuilder text = new StringBuilder();

        for (PostImage image : post.images) {
            text.append("Filename: ").append(image.filename).append(".").append(image.extension);
            if (image.isInlined) {
                text.append("\nLinked file");
            } else {
                text.append(" \nDimensions: ")
                        .append(image.imageWidth)
                        .append("x")
                        .append(image.imageHeight)
                        .append("\nSize: ")
                        .append(getReadableFileSize(image.size));
            }

            if (image.spoiler() && !image.isInlined) { //all linked files are spoilered, don't say that
                text.append("\nSpoilered");
            }

            text.append("\n");
        }

        text.append("Posted: ").append(PostHelper.getLocalDate(post));

        if (!TextUtils.isEmpty(post.id) && isBound() && chanLoader.getThread() != null) {
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

        new AlertDialog.Builder(context).setTitle(R.string.post_info_title)
                .setMessage(text.toString())
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showPosts() {
        showPosts(false);
    }

    private void showPosts(boolean refreshAfterHideOrRemovePosts) {
        if (chanLoader != null && chanLoader.getThread() != null) {
            threadPresenterCallback.showPosts(chanLoader.getThread(),
                    new PostsFilter(order, searchQuery),
                    refreshAfterHideOrRemovePosts
            );
        }
    }

    private void addHistory() {
        if (!isBound() || chanLoader.getThread() == null) {
            return;
        }

        if (!historyAdded && addToLocalBackHistory && ChanSettings.historyEnabled.get() && loadable.isThreadMode()
                // Do not attempt to add a saved thread to the history
                && !loadable.isLocal()) {
            historyAdded = true;
            History history = new History();
            history.loadable = loadable;
            PostImage opImage = chanLoader.getThread().getOp().image();
            history.thumbnailUrl = opImage != null ? opImage.getThumbnailUrl() : null;
            databaseManager.runTaskAsync(databaseManager.getDatabaseHistoryManager().addHistory(history));
        }
    }

    public void hideOrRemovePosts(boolean hide, boolean wholeChain, Post post, int threadNo) {
        Set<Post> posts = new HashSet<>();

        if (isBound()) {
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
        if (!isBound() || chanLoader.getThread() == null || loadable.isCatalogMode()) {
            return;
        }

        threadPresenterCallback.viewRemovedPostsForTheThread(chanLoader.getThread().getPosts(), loadable.no);
    }

    public void onRestoreRemovedPostsClicked(List<Integer> selectedPosts) {
        if (!isBound()) return;

        threadPresenterCallback.onRestoreRemovedPostsClicked(loadable, selectedPosts);
    }

    @Override
    public void openArchive(Pair<String, String> domainNamePair) {
        if (isBound()) {
            String link = loadable.desktopUrl();
            link = link.replace("https://boards.4chan.org/", "https://" + domainNamePair.second + "/");
            openLinkInBrowser(context, link);
        }
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void updateLoadable(Loadable.LoadableDownloadingState loadableDownloadingState) {
        if (isBound()) {
            loadable.setLoadableState(loadableDownloadingState);
        }
    }

    public void markAllPostsAsSeen() {
        if (!isBound()) return;
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

        void filterPostSubject(String subject);

        void filterPostName(String name);

        void filterPostComment(CharSequence comment);

        void filterPostID(String ID);

        void filterPostCountryCode(Post post);

        void filterPostFilename(Post post);

        void filterPostTripcode(String tripcode);

        void filterPostImageHash(Post post);

        void selectPost(int post);

        void showSearch(boolean show);

        void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void quote(Post post, CharSequence text);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post, int threadNo, boolean hide);

        void showNewPostsNotification(boolean show, int more);

        void showHideOrRemoveWholeChainDialog(boolean hide, Post post, int threadNo);

        void hideOrRemovePosts(boolean hide, boolean wholeChain, Set<Post> posts, int threadNo);

        void unhideOrUnremovePost(Post post);

        void viewRemovedPostsForTheThread(List<Post> threadPosts, int threadNo);

        void onRestoreRemovedPostsClicked(Loadable threadLoadable, List<Integer> selectedPosts);
    }
}
