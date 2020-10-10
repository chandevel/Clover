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

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.k1rakishou.fsaf.FileManager;

import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu.OVERFLOW_ID;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class ViewThreadController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback, ArchivesLayout.Callback, ToolbarMenuItem.OverflowMenuCallback {
    private static final int PIN_ID = 1;
    private static final int REPLY_ID = 2;
    private static final int ARCHIVE_ID = 3;
    private static final int REMOVED_ID = 4;

    @Inject
    WatchManager watchManager;
    @Inject
    FileManager fileManager;

    private boolean pinItemPinned = false;
    private Loadable loadable;

    //pairs of the current thread loadable and the thread we're going to's hashcode
    private Deque<Pair<Loadable, Integer>> threadFollowerpool = new ArrayDeque<>();

    @Nullable
    private HintPopup hintPopup = null;

    private FloatingMenu floatingMenu;

    public ViewThreadController(Context context, Loadable loadable) {
        super(context);
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigation.hasDrawer = true;

        buildMenu();
        loadThread(loadable);
    }

    protected void buildMenu() {
        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();

        if (!ChanSettings.textOnly.get()) {
            Drawable imageWhite = context.getDrawable(R.drawable.ic_image_themed_24dp);
            imageWhite.setTint(Color.WHITE);
            menuBuilder.withItem(-1, imageWhite, this::albumClicked);
        }
        menuBuilder.withItem(PIN_ID, R.drawable.ic_bookmark_border_white_24dp, this::pinClicked);

        NavigationItem.MenuOverflowBuilder menuOverflowBuilder = menuBuilder.withOverflow(this);

        if (!ChanSettings.enableReplyFab.get()) {
            menuOverflowBuilder.withSubItem(REPLY_ID, R.string.action_reply, () -> threadLayout.openReply(true));
        }

        menuOverflowBuilder.withSubItem(R.string.action_search,
                () -> ((ToolbarNavigationController) navigationController).showSearch()
        )
                .withSubItem(R.string.action_reload, () -> threadLayout.getPresenter().requestData());
        if (loadable.site instanceof Chan4) { //archives are 4chan only
            menuOverflowBuilder.withSubItem(ARCHIVE_ID,
                    R.string.thread_view_external_archive,
                    () -> threadLayout.getPresenter().showArchives(loadable.board.code, loadable.no, -1)
            );
        }
        menuOverflowBuilder.withSubItem(REMOVED_ID,
                R.string.view_removed_posts,
                () -> threadLayout.getPresenter().showRemovedPostsDialog()
        )
                .withSubItem(R.string.view_my_posts, this::showYourPosts)
                .withSubItem(R.string.action_open_browser, this::openBrowserClicked)
                .withSubItem(R.string.action_share, this::shareClicked)
                .withSubItem(R.string.action_scroll_to_top, () -> threadLayout.scrollTo(0, false))
                .withSubItem(R.string.action_scroll_to_bottom, () -> threadLayout.scrollTo(-1, false));

        menuOverflowBuilder.build().build();
    }

    private void albumClicked(ToolbarMenuItem item) {
        dismissFloatingMenu();
        threadLayout.getPresenter().showAlbum();
    }

    private void pinClicked(ToolbarMenuItem item) {
        dismissFloatingMenu();
        if (threadLayout.getPresenter().pin()) {
            setPinIconState(true);

            updateDrawerHighlighting(loadable);
        }
    }

    public void showYourPosts() {
        if (!threadLayout.getPresenter().isBound() || threadLayout.getPresenter().getChanThread() == null) return;
        List<Post> yourPosts = new ArrayList<>();
        for (Post post : threadLayout.getPresenter().getChanThread().getPosts()) {
            if (post.isSavedReply) yourPosts.add(post);
        }

        if (yourPosts.isEmpty()) {
            showToast(context, R.string.no_saved_posts_for_current_thread);
        } else {
            threadLayout.showPostsPopup(null, yourPosts);
        }
    }

    private void openBrowserClicked() {
        if (threadLayout.getPresenter().getChanThread() == null) {
            showToast(context, R.string.cannot_open_in_browser_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.getPresenter().getLoadable();
        openLinkInBrowser(context, loadable.desktopUrl());
    }

    private void shareClicked() {
        if (threadLayout.getPresenter().getChanThread() == null) {
            showToast(context, R.string.cannot_shared_thread_already_deleted);
            return;
        }

        Loadable loadable = threadLayout.getPresenter().getLoadable();
        shareLink(loadable.desktopUrl());
    }

    @Override
    public void onShow() {
        super.onShow();

        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconState(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        dismissHintPopup();
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
    }

    @Subscribe
    public void onEvent(PinMessages.PinRemovedMessage message) {
        setPinIconState(true);
    }

    @Subscribe
    public void onEvent(PinMessages.PinChangedMessage message) {
        setPinIconState(false);
    }

    @Subscribe
    public void onEvent(PinMessages.PinsChangedMessage message) {
        setPinIconState(true);
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        if (threadLoadable.site instanceof ExternalSiteArchive && !loadable.site.equals(threadLoadable.site)) {
            showThreadInternal(threadLoadable);
        } else {
            new AlertDialog.Builder(context).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which) -> showThreadInternal(threadLoadable))
                    .setTitle(!(threadLoadable.site instanceof ExternalSiteArchive)
                            ? R.string.open_thread_confirmation
                            : R.string.open_archived_thread_confirmation)
                    .setMessage("/" + threadLoadable.boardCode + "/" + threadLoadable.no + (
                            threadLoadable.markedNo != -1 && threadLoadable.markedNo != threadLoadable.no
                                    ? " #" + threadLoadable.markedNo
                                    : ""))
                    .show();
        }
    }

    private void showThreadInternal(final Loadable threadLoadable) {
        threadFollowerpool.addFirst(new Pair<>(loadable, threadLoadable.hashCode()));
        loadThread(threadLoadable);
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
            //slide or phone layout
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
        }
    }

    public void loadThread(Loadable loadable) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (!loadable.equals(presenter.getLoadable())) {
            loadThreadInternal(loadable);
        }
    }

    private void loadThreadInternal(Loadable loadable) {
        ThreadPresenter presenter = threadLayout.getPresenter();

        presenter.bindLoadable(loadable);
        this.loadable = loadable;

        navigation.title = loadable.title;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);

        ToolbarMenuSubItem reply = navigation.findSubItem(REPLY_ID);
        if (reply != null) {
            reply.enabled = loadable.site.siteFeature(Site.SiteFeature.POSTING);
        }

        ToolbarMenuSubItem archives = navigation.findSubItem(ARCHIVE_ID);
        if (archives != null) {
            archives.enabled = loadable.site instanceof Chan4;
        }

        ToolbarMenuSubItem removed = navigation.findSubItem(REMOVED_ID);
        if (removed != null) {
            removed.enabled = !(loadable.site instanceof ExternalSiteArchive);
        }

        ToolbarMenuItem item = navigation.findItem(PIN_ID);
        item.setVisible(!(loadable.site instanceof ExternalSiteArchive));
        ((ToolbarNavigationController) navigationController).toolbar.invalidate();

        setPinIconState(false);

        updateDrawerHighlighting(loadable);
        updateLeftPaneHighlighting(loadable);
        presenter.requestInitialData();

        showHints();
    }

    private void showHints() {
        int counter = ChanSettings.threadOpenCounter.increase();
        if (counter == 2) {
            view.postDelayed(() -> {
                View view = navigation.findItem(OVERFLOW_ID).getView();
                if (view != null) {
                    dismissHintPopup();
                    hintPopup = HintPopup.show(context, view, getString(R.string.thread_up_down_hint), -dp(1), 0);
                }
            }, 600);
        } else if (counter == 3) {
            view.postDelayed(() -> {
                View view = navigation.findItem(PIN_ID).getView();
                if (view != null) {
                    dismissHintPopup();
                    hintPopup = HintPopup.show(context, view, getString(R.string.thread_pin_hint), -dp(1), 0);
                }
            }, 600);
        }
    }

    private void dismissHintPopup() {
        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }
    }

    private void dismissFloatingMenu() {
        if (floatingMenu != null) {
            floatingMenu.dismiss();
            floatingMenu = null;
        }
    }

    @Override
    public void onShowPosts(Loadable loadable) {
        super.onShowPosts(loadable);
        setPinIconState(false);
        navigation.title = loadable.title;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
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

    private void setPinIconStateDrawable(boolean pinned, boolean animated) {
        if (pinned == pinItemPinned) {
            return;
        }
        ToolbarMenuItem menuItem = navigation.findItem(PIN_ID);
        if (menuItem == null) {
            return;
        }

        pinItemPinned = pinned;

        Drawable outline = context.getDrawable(R.drawable.ic_bookmark_border_white_24dp);
        Drawable white = context.getDrawable(R.drawable.ic_bookmark_white_24dp);

        Drawable drawable = pinned ? white : outline;
        menuItem.setImage(drawable, animated);
    }

    @Override
    public void openArchive(ExternalSiteArchive externalSiteArchive, String boardCode, int opNo, int postNo) {
        threadFollowerpool.addFirst(new Pair<>(loadable,
                externalSiteArchive.getArchiveLoadable(boardCode, opNo, postNo).hashCode()
        ));
        threadLayout.getPresenter().openArchive(externalSiteArchive, boardCode, opNo, postNo);
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
        //noinspection ConstantConditions
        loadThread(threadFollowerpool.removeFirst().first);
        return true;
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.getPresenter().getPostFromPostImage(postImage);
    }

    @Override
    public void onMenuShown(FloatingMenu<ToolbarMenuSubItem> menu) {
        dismissFloatingMenu();
        floatingMenu = menu;
    }

    @Override
    public void onMenuHidden() {
    }
}
