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
package com.github.adamantcheese.chan.ui.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.utils.AnimationUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu.OVERFLOW_ID;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ViewThreadController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback, ArchivesLayout.Callback {
    private static final String TAG = "ViewThreadController";

    private static final int PIN_ID = 1;
    private static final int SAVE_THREAD_ID = 2;

    private static final int VIEW_LOCAL_COPY_SUBMENU_ID = 1000;
    private static final int VIEW_LIVE_COPY_SUBMENU_ID = 1001;

    @Inject
    WatchManager watchManager;
    @Inject
    FileManager fileManager;

    private boolean pinItemPinned = false;
    private DownloadThreadState prevState = DownloadThreadState.Default;
    private Loadable loadable;

    //pairs of the current thread loadable and the thread we're going to's hashcode
    private Deque<Pair<Loadable, Integer>> threadFollowerpool = new ArrayDeque<>();

    private Drawable downloadIconOutline;
    private Drawable downloadIconFilled;
    private AnimatedVectorDrawableCompat downloadAnimation;

    private Animatable2Compat.AnimationCallback downloadAnimationCallback = new Animatable2Compat.AnimationCallback() {
        @Override
        public void onAnimationEnd(Drawable drawable) {
            super.onAnimationEnd(drawable);

            downloadAnimation.start();
        }
    };

    public ViewThreadController(Context context) {
        super(context);
    }

    public void setLoadable(Loadable loadable) {
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        downloadAnimation =
                (AnimatedVectorDrawableCompat) AnimationUtils.createAnimatedDownloadIcon(context, Color.WHITE).mutate();

        downloadIconOutline = context.getDrawable(R.drawable.ic_download_anim0);
        downloadIconOutline.setTint(Color.WHITE);

        downloadIconFilled = context.getDrawable(R.drawable.ic_download_anim1);
        downloadIconFilled.setTint(Color.WHITE);

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigation.hasDrawer = true;

        buildMenu();
        loadThread(loadable);
    }

    protected void buildMenu() {
        prevState = DownloadThreadState.Default;

        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu()
                .withItem(R.drawable.ic_image_white_24dp, this::albumClicked)
                .withItem(PIN_ID, R.drawable.ic_bookmark_outline_white_24dp, this::pinClicked);

        if (ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            // This method recreates the menu (and if there was the download animation running it
            // will be reset to the default icon). We need to reset the prev state as well so that
            // we can start animation again
            menuBuilder.withItem(SAVE_THREAD_ID, downloadIconOutline, this::saveClicked);
        }

        NavigationItem.MenuOverflowBuilder menuOverflowBuilder = menuBuilder.withOverflow();

        if (!ChanSettings.enableReplyFab.get()) {
            menuOverflowBuilder.withSubItem(R.string.action_reply, this::replyClicked);
        }

        menuOverflowBuilder.withSubItem(R.string.action_search, this::searchClicked)
                .withSubItem(R.string.action_reload, this::reloadClicked)
                .withSubItem(R.string.thread_show_archives, this::showArchives)
                .withSubItem(R.string.view_removed_posts, this::showRemovedPostsDialog)
                .withSubItem(R.string.action_open_browser, this::openBrowserClicked)
                .withSubItem(R.string.action_share, this::shareClicked)
                .withSubItem(R.string.action_scroll_to_top, this::upClicked)
                .withSubItem(R.string.action_scroll_to_bottom, this::downClicked);

        // These items are dynamic; create them here by default if settings permit
        if (ChanSettings.incrementalThreadDownloadingEnabled.get()
                && getThreadDownloadState() != DownloadThreadState.Default) {
            menuOverflowBuilder.withSubItem(VIEW_LOCAL_COPY_SUBMENU_ID,
                    R.string.view_local_version,
                    false,
                    this::handleClickViewLocalVersion
            );

            menuOverflowBuilder.withSubItem(VIEW_LIVE_COPY_SUBMENU_ID,
                    R.string.view_view_version,
                    false,
                    this::handleClickViewLiveVersion
            );
        }

        menuOverflowBuilder.build().build();
    }

    private void albumClicked(ToolbarMenuItem item) {
        threadLayout.getPresenter().showAlbum();
    }

    private void pinClicked(ToolbarMenuItem item) {
        if (threadLayout.getPresenter().pin()) {
            setPinIconState(true);
            setSaveIconState(true);

            updateDrawerHighlighting(loadable);
        }
    }

    private void saveClicked(ToolbarMenuItem item) {
        if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.DownloadingAndViewable) {
            // Too many problems with this thing, just disable it while viewing downloading thread
            return;
        }

        RuntimePermissionsHelper runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            saveClickedInternal();
            return;
        }

        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (granted) {
                saveClickedInternal();
            } else {
                showToast(R.string.view_thread_controller_thread_downloading_requires_write_permission,
                        Toast.LENGTH_LONG
                );
            }
        });
    }

    private void saveClickedInternal() {
        AbstractFile baseLocalThreadsDir = fileManager.newBaseDirectoryFile(LocalThreadsBaseDirectory.class);

        if (baseLocalThreadsDir == null) {
            Logger.e(TAG, "saveClickedInternal() fileManager.newLocalThreadFile() returned null");
            showToast(R.string.local_threads_base_dir_does_not_exist, Toast.LENGTH_LONG);
            return;
        }

        if (!fileManager.exists(baseLocalThreadsDir) && fileManager.create(baseLocalThreadsDir) == null) {
            Logger.e(TAG, "saveClickedInternal() Couldn't create baseLocalThreadsDir");
            showToast(R.string.could_not_create_base_local_threads_dir, Toast.LENGTH_LONG);
            return;
        }

        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
            Logger.e(TAG, "Base local threads directory does not exist");
            showToast(R.string.local_threads_base_dir_does_not_exist, Toast.LENGTH_LONG);
            return;
        }

        if (threadLayout.getPresenter().save()) {
            updateDrawerHighlighting(loadable);
            populateLocalOrLiveVersionMenu();

            // Update icon at the very end, otherwise it won't start animating at all
            setSaveIconState(true);
        }
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void replyClicked(ToolbarMenuSubItem item) {
        threadLayout.openReply(true);
    }

    private void reloadClicked(ToolbarMenuSubItem item) {
        threadLayout.getPresenter().requestData();
    }

    public void showArchives(ToolbarMenuSubItem item) {
        @SuppressLint("InflateParams")
        final ArchivesLayout dialogView = (ArchivesLayout) inflate(context, R.layout.layout_archives, null);
        dialogView.setBoard(threadLayout.getPresenter().getLoadable().board);
        dialogView.setCallback(this);

        AlertDialog dialog =
                new AlertDialog.Builder(context).setView(dialogView).setTitle(R.string.thread_show_archives).create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    public void showRemovedPostsDialog(ToolbarMenuSubItem item) {
        threadLayout.getPresenter().showRemovedPostsDialog();
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        if (threadLayout.getPresenter().getChanThread() == null) {
            showToast(R.string.cannot_open_in_browser_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.getPresenter().getLoadable();
        openLinkInBrowser((Activity) context, loadable.desktopUrl());
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        if (threadLayout.getPresenter().getChanThread() == null) {
            showToast(R.string.cannot_shared_thread_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.getPresenter().getLoadable();
        shareLink(loadable.desktopUrl());
    }

    private void upClicked(ToolbarMenuSubItem item) {
        threadLayout.scrollTo(0, false);
    }

    private void downClicked(ToolbarMenuSubItem item) {
        threadLayout.scrollTo(-1, false);
    }

    /**
     * Replaces the current live thread with the local thread
     */
    private void handleClickViewLocalVersion(ToolbarMenuSubItem item) {
        if (loadable.loadableDownloadingState != Loadable.LoadableDownloadingState.DownloadingAndNotViewable) {
            populateLocalOrLiveVersionMenu();
            return;
        }

        loadable.loadableDownloadingState = Loadable.LoadableDownloadingState.DownloadingAndViewable;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            pin.loadable.loadableDownloadingState = Loadable.LoadableDownloadingState.DownloadingAndViewable;
            watchManager.updatePin(pin);
        }

        threadLayout.getPresenter().requestData();
    }

    /**
     * Replaces the current local thread with the live thread
     */
    private void handleClickViewLiveVersion(ToolbarMenuSubItem item) {
        if (loadable.loadableDownloadingState != Loadable.LoadableDownloadingState.DownloadingAndViewable) {
            populateLocalOrLiveVersionMenu();
            return;
        }

        loadable.loadableDownloadingState = Loadable.LoadableDownloadingState.DownloadingAndNotViewable;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            pin.loadable.loadableDownloadingState = Loadable.LoadableDownloadingState.DownloadingAndNotViewable;
            watchManager.updatePin(pin);
        }

        threadLayout.getPresenter().requestData();
    }

    @Override
    public void onShow() {
        super.onShow();

        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconState(false);
            setSaveIconState(false);
        }
    }

    @Override
    public void onHide() {
        super.onHide();

        downloadAnimation.unregisterAnimationCallback(downloadAnimationCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateDrawerHighlighting(null);
        updateLeftPaneHighlighting(null);
    }

    @Override
    public void openPin(Pin pin) {
        loadThread(pin.loadable);
    }

    @Subscribe
    public void onEvent(PinMessages.PinAddedMessage message) {
        setPinIconState(true);
        setSaveIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        setPinIconState(true);
        setSaveIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        setPinIconState(false);
        setSaveIconState(false);

        // Does this ever happen?
        // Update title
        if (message.pin.loadable == loadable) {
            onShowPosts();
        }
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        setPinIconState(true);
        setSaveIconState(true);
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        new AlertDialog.Builder(context).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    threadFollowerpool.addFirst(new Pair<>(loadable, threadLoadable.hashCode()));
                    loadThread(threadLoadable);
                })
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.boardCode + "/" + threadLoadable.no)
                .show();
    }

    @Override
    public void showBoard(final Loadable catalogLoadable) {
        showBoardInternal(catalogLoadable, null);
    }

    @Override
    public void showBoardAndSearch(final Loadable catalogLoadable, String search) {
        showBoardInternal(catalogLoadable, search);
    }

    private void showBoardInternal(Loadable catalogLoadable, String searchQuery) {
        if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof BrowseController) {
            //slide layout
            doubleNavigationController.switchToController(true);
            ((BrowseController) doubleNavigationController.getLeftController()).setBoard(catalogLoadable.board);
            if (searchQuery != null) {
                ((BrowseController) doubleNavigationController.getLeftController()).searchQuery = searchQuery;
            }
        } else if (doubleNavigationController != null
                && doubleNavigationController.getLeftController() instanceof StyledToolbarNavigationController) {
            //split layout
            ((BrowseController) doubleNavigationController.getLeftController().childControllers.get(0)).setBoard(
                    catalogLoadable.board);
            if (searchQuery != null) {
                Toolbar toolbar = doubleNavigationController.getLeftController().childControllers.get(0).getToolbar();
                if (toolbar != null) {
                    toolbar.openSearch();
                    toolbar.searchInput(searchQuery);
                }
            }
        } else {
            //phone layout
            BrowseController browseController = null;
            for (Controller c : navigationController.childControllers) {
                if (c instanceof BrowseController) {
                    browseController = (BrowseController) c;
                    break;
                }
            }
            if (browseController != null) {
                browseController.setBoard(catalogLoadable.board);
            }
            navigationController.popController(false);
            //search after we're at the browse controller
            if (searchQuery != null && browseController != null) {
                Toolbar toolbar = browseController.getToolbar();
                if (toolbar != null) {
                    toolbar.openSearch();
                    toolbar.searchInput(searchQuery);
                }
            }
        }
    }

    public void loadThread(Loadable loadable, boolean addToLocalBackHistory) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (!loadable.equals(presenter.getLoadable())) {
            loadThreadInternal(loadable, addToLocalBackHistory);
            return;
        }

        //if we're toggling local/live we need to rebuild the menu
        populateLocalOrLiveVersionMenu();
    }

    private void loadThreadInternal(Loadable loadable, boolean addToLocalBackHistory) {
        ThreadPresenter presenter = threadLayout.getPresenter();

        presenter.bindLoadable(loadable, addToLocalBackHistory);
        this.loadable = presenter.getLoadable();

        populateLocalOrLiveVersionMenu();
        navigation.title = loadable.title;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);

        setPinIconState(false);
        setSaveIconState(false);

        updateDrawerHighlighting(loadable);
        updateLeftPaneHighlighting(loadable);
        presenter.requestInitialData();

        showHints();
    }

    private void populateLocalOrLiveVersionMenu() {
        //setup the extra items if they're needed, or remove as necessary
        if (ChanSettings.incrementalThreadDownloadingEnabled.get()
                && getThreadDownloadState() != DownloadThreadState.Default) {
            ToolbarMenuItem overflowMenu = navigation.findItem(OVERFLOW_ID);
            if (navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID) == null
                    && navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID) == null) {
                overflowMenu.addSubItem(new ToolbarMenuSubItem(VIEW_LOCAL_COPY_SUBMENU_ID,
                        R.string.view_local_version,
                        true,
                        this::handleClickViewLocalVersion
                ));
                overflowMenu.addSubItem(new ToolbarMenuSubItem(VIEW_LIVE_COPY_SUBMENU_ID,
                        R.string.view_view_version,
                        true,
                        this::handleClickViewLiveVersion
                ));
            }
        } else {
            ToolbarMenuItem overflowMenu = navigation.findItem(OVERFLOW_ID);
            overflowMenu.removeSubItem(navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID));
            overflowMenu.removeSubItem(navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID));
        }

        try {
            Pin pin = watchManager.findPinByLoadableId(loadable.id);
            if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
                // No pin for this loadable we are probably not downloading this thread.
                // Pin has no downloading flag.
                // Disable menu items.
                navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID).enabled = false;
                navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID).enabled = false;
                return;
            }

            SavedThread savedThread = watchManager.findSavedThreadByLoadableId(loadable.id);
            if (savedThread == null || savedThread.isFullyDownloaded
                    || loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.AlreadyDownloaded
                    || loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.NotDownloading) {
                // No saved thread.
                // Saved thread fully downloaded.
                // Not downloading thread currently.
                // Disable menu items.
                navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID).enabled = false;
                navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID).enabled = false;
                return;
            }

            if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.DownloadingAndNotViewable) {
                navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID).enabled = true;
                navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID).enabled = false;
            } else if (loadable.loadableDownloadingState == Loadable.LoadableDownloadingState.DownloadingAndViewable) {
                navigation.findSubItem(VIEW_LOCAL_COPY_SUBMENU_ID).enabled = false;
                navigation.findSubItem(VIEW_LIVE_COPY_SUBMENU_ID).enabled = true;
            }
        } catch (NullPointerException ignored) {
            // Ignore NPE because the menu ID doesn't exist for the subitem
        }
    }

    public void loadThread(Loadable loadable) {
        loadThread(loadable, true);
    }

    private void showHints() {
        int counter = ChanSettings.threadOpenCounter.increase();
        if (counter == 2) {
            view.postDelayed(() -> {
                View view = navigation.findItem(OVERFLOW_ID).getView();
                if (view != null) {
                    HintPopup.show(context, view, getString(R.string.thread_up_down_hint), -dp(1), 0);
                }
            }, 600);
        } else if (counter == 3) {
            view.postDelayed(() -> {
                View view = navigation.findItem(PIN_ID).getView();
                if (view != null) {
                    HintPopup.show(context, view, getString(R.string.thread_pin_hint), -dp(1), 0);
                }
            }, 600);
        } else if (counter == 4) {
            view.postDelayed(() -> {
                ToolbarMenuItem saveThreadItem = navigation.findItem(SAVE_THREAD_ID);
                if (saveThreadItem != null) {
                    View view = saveThreadItem.getView();
                    if (view != null) {
                        HintPopup.show(context, view, getString(R.string.thread_save_hint), -dp(1), 0);
                    }
                }
            }, 600);
        }
    }

    @Override
    public void onShowPosts() {
        super.onShowPosts();
        navigation.title = this.loadable.title;

        setPinIconState(false);
        setSaveIconState(false);
        populateLocalOrLiveVersionMenu();

        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
        ((ToolbarNavigationController) navigationController).toolbar.updateViewForItem(navigation);
    }

    private void updateDrawerHighlighting(Loadable loadable) {
        Pin pin = loadable == null ? null : watchManager.findPinByLoadableId(loadable.id);

        if (navigationController.parentController instanceof DrawerController) {
            ((DrawerController) navigationController.parentController).setPinHighlighted(pin);
        } else if (doubleNavigationController != null) {
            Controller doubleNav = (Controller) doubleNavigationController;
            if (doubleNav.parentController instanceof DrawerController) {
                ((DrawerController) doubleNav.parentController).setPinHighlighted(pin);
            }
        }
    }

    private void updateLeftPaneHighlighting(Loadable loadable) {
        if (doubleNavigationController != null) {
            ThreadController threadController = null;
            Controller leftController = doubleNavigationController.getLeftController();
            if (leftController instanceof ThreadController) {
                threadController = (ThreadController) leftController;
            } else if (leftController instanceof NavigationController) {
                NavigationController leftNavigationController = (NavigationController) leftController;
                for (Controller controller : leftNavigationController.childControllers) {
                    if (controller instanceof ThreadController) {
                        threadController = (ThreadController) controller;
                        break;
                    }
                }
            }
            if (threadController != null) {
                threadController.selectPost(loadable != null ? loadable.no : -1);
            }
        }
    }

    private void setPinIconState(boolean animated) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconStateDrawable(presenter.isPinned(), animated);
        }
    }

    private void setSaveIconState(boolean animated) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setSaveIconStateDrawable(getThreadDownloadState(), animated);
        }
    }

    private DownloadThreadState getThreadDownloadState() {
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin == null || !PinType.hasDownloadFlag(pin.pinType)) {
            return DownloadThreadState.Default;
        }

        SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
        if (savedThread == null) {
            return DownloadThreadState.Default;
        }

        if (savedThread.isFullyDownloaded || savedThread.isStopped) {
            return DownloadThreadState.FullyDownloaded;
        }

        return DownloadThreadState.DownloadInProgress;
    }

    private void setSaveIconStateDrawable(
            DownloadThreadState downloadThreadState, boolean animated
    ) {
        if (downloadThreadState == prevState) {
            return;
        }

        ToolbarMenuItem menuItem = navigation.findItem(SAVE_THREAD_ID);
        if (menuItem == null) {
            return;
        }

        prevState = downloadThreadState;

        switch (downloadThreadState) {
            case Default:
                downloadAnimation.stop();
                downloadAnimation.clearAnimationCallbacks();

                menuItem.setImage(downloadIconOutline, animated);
                break;
            case DownloadInProgress:
                menuItem.setImage(downloadAnimation, animated);
                downloadAnimation.start();

                // Don't forget to remove the old callback before adding a new one
                downloadAnimation.unregisterAnimationCallback(downloadAnimationCallback);
                downloadAnimation.registerAnimationCallback(downloadAnimationCallback);
                break;
            case FullyDownloaded:
                downloadAnimation.stop();
                downloadAnimation.clearAnimationCallbacks();

                menuItem.setImage(downloadIconFilled, animated);
                break;
        }
    }

    private void setPinIconStateDrawable(boolean pinned, boolean animated) {
        if (pinned == pinItemPinned) {
            return;
        }
        ToolbarMenuItem menuItem = navigation.findItem(PIN_ID);
        if (menuItem == null) {
            return;
        }

        pinItemPinned = pinned;

        Drawable outline = context.getDrawable(R.drawable.ic_bookmark_outline_white_24dp);
        Drawable white = context.getDrawable(R.drawable.ic_bookmark_white_24dp);

        Drawable drawable = pinned ? white : outline;
        menuItem.setImage(drawable, animated);
    }

    @Override
    public void openArchive(Pair<String, String> domainNamePair) {
        Loadable loadable = threadLayout.getPresenter().getLoadable();
        String link = loadable.desktopUrl();
        link = link.replace("https://boards.4chan.org/", "https://" + domainNamePair.second + "/");
        openLinkInBrowser((Activity) context, link);
    }

    @Override
    public boolean threadBackPressed() {
        //clear the pool if the current thread isn't a part of this crosspost chain
        //ie a new thread is loaded and a new chain is started; this will never throw null pointer exceptions
        //noinspection ConstantConditions
        if (!threadFollowerpool.isEmpty() && threadFollowerpool.peekFirst().second != loadable.hashCode()) {
            threadFollowerpool.clear();
        }
        //if the thread is new, it'll be empty here, so we'll get back-to-catalog functionality
        if (threadFollowerpool.isEmpty()) {
            return false;
        }
        loadThread(threadFollowerpool.removeFirst().first, false);
        return true;
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.getPresenter().getPostFromPostImage(postImage);
    }

    public enum DownloadThreadState {
        Default,
        DownloadInProgress,
        FullyDownloaded
    }
}
