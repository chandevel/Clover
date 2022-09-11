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

import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.GRID;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.STAGGER;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAprilFoolsDay;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.BrowsePresenter;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter.PostsOrder;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.BrowseBoardsFloatingMenu;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.*;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.*;

import org.greenrobot.eventbus.Subscribe;

import java.util.*;

import javax.inject.Inject;

public class BrowseController
        extends ThreadController
        implements ThreadLayout.ThreadLayoutCallback, BrowsePresenter.Callback, BrowseBoardsFloatingMenu.ClickCallback,
                   ThreadSlideController.SlideChangeListener {
    private enum OverflowMenuId {
        VIEW_MODE,
        ARCHIVE,
        REPLY
    }

    @Inject
    BrowsePresenter presenter;

    // these together bodge together search term persistency between controllers and clear stuff out if you change boards
    public String searchQuery = null;
    public boolean clearNextSearch = false;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialization
        threadLayout.setPostViewMode(ChanSettings.boardViewMode.get());
        threadLayout.getPresenter().setOrder(ChanSettings.boardOrder.get());

        // Navigation
        initNavigation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroy();
    }

    @Override
    public void setBoard(Board board) {
        presenter.setBoard(board);
    }

    public void loadWithDefaultBoard() {
        presenter.loadWithDefaultBoard();
    }

    private void initNavigation() {
        // Navigation item
        navigation.hasDrawer = true;

        navigation.setMiddleMenu(anchor -> {
            BrowseBoardsFloatingMenu boardsFloatingMenu = new BrowseBoardsFloatingMenu(context);
            boardsFloatingMenu.show(view, anchor, BrowseController.this, presenter.currentBoard());
        });

        // Toolbar menu
        navigation.hasBack = false;

        // this controller is used for catalog views; displaying things on two rows for them middle menu is how we want it done
        // these need to be setup before the view is rendered, otherwise the subtitle view is removed
        navigation.title = "App Setup";
        navigation.subtitle = "Tap for site/board setup";

        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();
        if (ChanSettings.moveSortToToolbar.get()) {
            menuBuilder.withItem(R.drawable.ic_fluent_list_24_filled, this::handleSorting);
        }
        menuBuilder.withItem(R.drawable.ic_fluent_search_24_filled, (item) -> {
            if (threadLayout.getPresenter().isBound()) {
                ((ToolbarNavigationController) navigationController).showSearch();
            }
        }).withItem(R.drawable.animated_refresh_icon, this::reloadClicked);

        NavigationItem.MenuOverflowBuilder overflowBuilder = menuBuilder.withOverflow();

        if (!ChanSettings.enableReplyFab.get()) {
            overflowBuilder.withSubItem(OverflowMenuId.REPLY,
                    isAprilFoolsDay() ? R.string.action_reply_fools : R.string.action_reply,
                    () -> threadLayout.openReply(true)
            );
        }

        overflowBuilder.withSubItem(OverflowMenuId.VIEW_MODE,
                ChanSettings.boardViewMode.get() == PostViewMode.LIST
                        ? R.string.action_switch_catalog
                        : R.string.action_switch_board,
                this::toggleViewMode
        );

        if (!ChanSettings.moveSortToToolbar.get()) {
            overflowBuilder.withSubItem(R.string.action_sort, () -> handleSorting(null));
        }

        overflowBuilder
                .withSubItem(OverflowMenuId.ARCHIVE, R.string.thread_view_local_archive, this::openArchive)
                .withSubItem(R.string.action_open_browser, () -> handleShareAndOpenInBrowser(false))
                .withSubItem(R.string.action_share, () -> handleShareAndOpenInBrowser(true))
                .withSubItem(R.string.board_info, this::showBoardInfo)
                .withSubItem(R.string.action_scroll_to_top, () -> threadLayout.getPresenter().scrollTo(0, false))
                .withSubItem(R.string.action_scroll_to_bottom, () -> threadLayout.getPresenter().scrollTo(-1, false))
                .build()
                .build();

        // Presenter
        presenter.create(this);
    }

    private void reloadClicked(ToolbarMenuItem item) {
        threadLayout.getPresenter().requestData();
        ((AnimatedVectorDrawable) item.getView().getDrawable()).start();
    }

    @Override
    public void onSiteClicked(Site site) {
        presenter.onBoardsFloatingMenuSiteClicked(site);
    }

    @Override
    public void openSetup() {
        SitesSetupController setupController = new SitesSetupController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(setupController);
        } else {
            navigationController.pushController(setupController);
        }
    }

    private void openArchive() {
        Board board = presenter.currentBoard();
        if (board == null) {
            return;
        }

        ArchiveController archiveController = new ArchiveController(context, board);

        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(archiveController);
        } else {
            navigationController.pushController(archiveController);
        }
    }

    private void toggleViewMode() {
        PostViewMode postViewMode = ChanSettings.boardViewMode.get();
        if (postViewMode == PostViewMode.LIST) {
            postViewMode = ChanSettings.useStaggeredCatalogGrid.get() ? STAGGER : GRID;
        } else {
            postViewMode = PostViewMode.LIST;
        }

        ChanSettings.boardViewMode.set(postViewMode);

        int viewModeText =
                postViewMode == PostViewMode.LIST ? R.string.action_switch_catalog : R.string.action_switch_board;
        navigation.findSubItem(OverflowMenuId.VIEW_MODE).text = getString(viewModeText);

        threadLayout.setPostViewMode(postViewMode);
    }

    private void handleSorting(ToolbarMenuItem item) {
        final ThreadPresenter presenter = threadLayout.getPresenter();
        List<FloatingMenuItem<PostsFilter.PostsOrder>> items = new ArrayList<>();
        for (PostsFilter.PostsOrder postsOrder : PostsOrder.values()) {
            String name = StringUtils.caseAndSpace(postsOrder.name(), "_", true);
            if (postsOrder == ChanSettings.boardOrder.get()) {
                name = "\u2713 " + name; // Checkmark
            }

            items.add(new FloatingMenuItem<>(postsOrder, name));
        }
        ToolbarMenuItem overflow = navigation.findOverflow();
        View anchor = (item != null ? item : overflow).getView();
        FloatingMenu<PostsFilter.PostsOrder> menu;
        if (anchor != null) {
            menu = new FloatingMenu<>(context, anchor, items);
        } else {
            Logger.wtf(this, "Couldn't find anchor for sorting button action??");
            menu = new FloatingMenu<>(context, view, items);
        }

        menu.setCallback(new FloatingMenu.ClickCallback<PostsFilter.PostsOrder>() {
            @Override
            public void onFloatingMenuItemClicked(
                    FloatingMenu<PostsFilter.PostsOrder> menu, FloatingMenuItem<PostsFilter.PostsOrder> item
            ) {
                PostsFilter.PostsOrder postsOrder = item.getId();
                ChanSettings.boardOrder.set(postsOrder);
                presenter.setOrder(postsOrder);
            }
        });
        menu.show();
    }

    @Override
    public void loadBoard(Loadable loadable) {
        loadable.title = loadable.board.getFormattedName();
        navigation.title = "/" + loadable.boardCode + "/";
        navigation.subtitle = loadable.board.name;

        ThreadPresenter presenter = threadLayout.getPresenter();

        if (presenter.getLoadable() != null
                && !presenter.getLoadable().boardCode.equalsIgnoreCase(loadable.boardCode)) {
            clearNextSearch = true;
        }

        presenter.bindLoadable(loadable);

        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
        ToolbarMenuSubItem archive = navigation.findSubItem(OverflowMenuId.ARCHIVE);
        archive.enabled = loadable.board.site.boardFeature(Site.BoardFeature.ARCHIVE, loadable.board);
        ToolbarMenuSubItem reply = navigation.findSubItem(OverflowMenuId.REPLY);
        if (reply != null) {
            reply.enabled = loadable.board.site.siteFeature(Site.SiteFeature.POSTING);
        }
    }

    @Override
    public void loadSiteSetup(Site site) {
        SiteSetupController siteSetupController = new SiteSetupController(context, site);

        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(siteSetupController);
        } else {
            navigationController.pushController(siteSetupController);
        }
    }

    @Override
    public void showBoard(Loadable catalogLoadable) {
        //we don't actually need to do anything here because you can't tap board links in the browse controller
        //set the board just in case?
        setBoard(catalogLoadable.board);
    }

    @Override
    public void showBoardAndSearch(Loadable catalogLoadable, String searchQuery) {
        //we don't actually need to do anything here because you can't tap board links in the browse controller
        //set the board just in case?
        setBoard(catalogLoadable.board);
    }

    // Creates or updates the target ViewThreadController
    // This controller can be in various places depending on the layout, so we dynamically search for it
    @Override
    public void showThread(Loadable threadLoadable) {
        // The target ThreadViewController is in a split nav
        // (BrowseController -> ToolbarNavigationController -> SplitNavigationController)
        SplitNavigationController splitNav = null;

        // The target ThreadViewController is in a slide nav
        // (BrowseController -> SlideController -> ToolbarNavigationController)
        ThreadSlideController slideNav = null;

        if (doubleNavigationController instanceof SplitNavigationController) {
            splitNav = (SplitNavigationController) doubleNavigationController;
        }

        if (doubleNavigationController instanceof ThreadSlideController) {
            slideNav = (ThreadSlideController) doubleNavigationController;
        }

        if (splitNav != null) {
            // Create a threadview inside a toolbarnav in the right part of the split layout
            if (splitNav.getRightController() instanceof StyledToolbarNavigationController) {
                StyledToolbarNavigationController navigationController =
                        (StyledToolbarNavigationController) splitNav.getRightController();

                if (navigationController.getTop() instanceof ViewThreadController) {
                    ((ViewThreadController) navigationController.getTop()).loadThread(threadLoadable);
                    ((ViewThreadController) navigationController.getTop()).onNavItemSet();
                }
            } else {
                StyledToolbarNavigationController navigationController = new StyledToolbarNavigationController(context);
                splitNav.setRightController(navigationController);
                ViewThreadController viewThreadController = new ViewThreadController(context, threadLoadable);
                navigationController.pushController(viewThreadController, false);
            }
            splitNav.switchToController(false);
        } else if (slideNav != null) {
            // Create a threadview in the right part of the slide nav *without* a toolbar
            if (slideNav.getRightController() instanceof ViewThreadController) {
                ((ViewThreadController) slideNav.getRightController()).loadThread(threadLoadable);
            } else {
                ViewThreadController viewThreadController = new ViewThreadController(context, threadLoadable);
                slideNav.setRightController(viewThreadController);
            }
            slideNav.switchToController(false);
        }
    }

    @Override
    public void onNavItemSet() {
        super.onNavItemSet();

        if (getToolbar() != null) {
            if (!Objects.equals(navigation.searchText, searchQuery)) {
                if (TextUtils.isEmpty(searchQuery) && clearNextSearch) {
                    clearNextSearch = false;
                    getToolbar().closeSearch();
                    AndroidUtils.clearAnySelectionsAndKeyboards(context);
                    return;
                }
            }
            if (!TextUtils.isEmpty(searchQuery)) {
                navigation.searchText = searchQuery;
                getToolbar().openSearch();
                searchQuery = null;
            }
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        super.onSearchVisibilityChanged(visible);
        clearNextSearch = false;
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        return threadLayout.getPresenter().getPostFromPostImage(postImage);
    }

    private void showBoardInfo() {
        Board board = presenter.currentBoard();
        if (board == null) return;

        AlertDialog dialog = getDefaultAlertBuilder(context).setPositiveButton(R.string.ok, null).create();
        dialog.setCanceledOnTouchOutside(true);

        StringBuilder text = new StringBuilder();

        text
                .append('/')
                .append(board.code)
                .append('/')
                .append('\n')
                .append(board.name)
                .append("\n")
                .append(board.description)
                .append('\n');
        if (!board.workSafe) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && ChanSettings.enableEmoji.get()
                    && !PersistableChanState.noFunAllowed.get()) {
                text.append("\uD83D\uDD1E");
            } else {
                text.append("NSFW");
            }
            text.append(" Board").append('\n');
        }
        text.append(board.spoilers ? "Allows spoilered text and images" : "").append("\n");
        text.append("Bump limit: ").append(board.bumpLimit).append(" posts").append("\n");
        text.append("Image limit: ").append(board.imageLimit).append(" images").append("\n");
        text.append("Page limit: ").append(board.pages).append("\n");

        text.append("New thread cooldown: ").append(board.cooldownThreads).append(" seconds").append("\n");
        text.append("New reply cooldown: ").append(board.cooldownReplies).append(" seconds").append("\n");
        text.append("Image reply cooldown: ").append(board.cooldownImages).append(" seconds").append("\n");

        dialog.setMessage(text);
        dialog.show();
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        PostViewMode currentBoardViewMode = ChanSettings.boardViewMode.get();
        if (currentBoardViewMode == GRID || currentBoardViewMode == STAGGER) {
            PostViewMode newMode = ChanSettings.useStaggeredCatalogGrid.get() ? STAGGER : GRID;
            ChanSettings.boardViewMode.set(newMode);
            threadLayout.setPostViewMode(newMode);
        }
        super.onEvent(message);
    }
}
