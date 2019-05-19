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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.BrowsePresenter;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.BrowseBoardsFloatingMenu;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class BrowseController extends ThreadController implements
        ThreadLayout.ThreadLayoutCallback,
        BrowsePresenter.Callback,
        BrowseBoardsFloatingMenu.ClickCallback {
    private static final int VIEW_MODE_ID = 1;
    private static final int ARCHIVE_ID = 2;

    @Inject
    BrowsePresenter presenter;

    private ChanSettings.PostViewMode postViewMode;
    private PostsFilter.Order order;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        // Initialization
        postViewMode = ChanSettings.boardViewMode.get();
        order = PostsFilter.Order.find(ChanSettings.boardOrder.get());
        threadLayout.setPostViewMode(postViewMode);
        threadLayout.getPresenter().setOrder(order);

        // Navigation
        initNavigation();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        presenter.destroy();
    }

    @Override
    public void showSitesNotSetup() {
        super.showSitesNotSetup();

        HintPopup hint = HintPopup.show(context, getToolbar(), R.string.thread_empty_setup_hint);
        hint.alignLeft();
        hint.wiggle();
    }

    public void setBoard(Board board) {
        presenter.setBoard(board);
    }

    public void loadWithDefaultBoard() {
        presenter.loadWithDefaultBoard();
    }

    private void initNavigation() {
        // Navigation item
        navigation.hasDrawer = true;

        setupMiddleNavigation();

        // Toolbar menu
        navigation.hasBack = false;

        NavigationItem.MenuOverflowBuilder overflowBuilder = navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .withItem(R.drawable.ic_refresh_white_24dp, this::reloadClicked)
                .withOverflow();

        if (!ChanSettings.enableReplyFab.get()) {
            overflowBuilder.withSubItem(R.string.action_reply, this::replyClicked);
        }

        overflowBuilder.withSubItem(VIEW_MODE_ID,
                postViewMode == ChanSettings.PostViewMode.LIST ?
                        R.string.action_switch_catalog : R.string.action_switch_board,
                this::viewModeClicked);

        overflowBuilder
                .withSubItem(ARCHIVE_ID, R.string.thread_view_archive, this::archiveClicked)
                .withSubItem(R.string.action_sort, this::orderClicked)
                .withSubItem(R.string.action_open_browser, this::openBrowserClicked)
                .withSubItem(R.string.action_share, this::shareClicked)
                .build()
                .build();

        // Presenter
        presenter.create(this);
    }

    private void searchClicked(ToolbarMenuItem item) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter.isBound()) {
            View refreshView = item.getView();
            refreshView.setScaleX(1f);
            refreshView.setScaleY(1f);
            refreshView.animate()
                    .scaleX(10f)
                    .scaleY(10f)
                    .setDuration(500)
                    .setInterpolator(new AccelerateInterpolator(2f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            refreshView.setScaleX(1f);
                            refreshView.setScaleY(1f);
                        }
                    });

            ((ToolbarNavigationController) navigationController).showSearch();
        }
    }

    private void reloadClicked(ToolbarMenuItem item) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter.isBound()) {
            presenter.requestData();

            // Give the rotation menu item view a spin.
            View refreshView = item.getView();
            refreshView.setRotation(0f);
            refreshView.animate()
                    .rotation(360f)
                    .setDuration(500)
                    .setInterpolator(new DecelerateInterpolator(2f));
        }
    }

    private void replyClicked(ToolbarMenuSubItem item) {
        threadLayout.openReply(true);
    }

    private void viewModeClicked(ToolbarMenuSubItem item) {
        handleViewMode(item);
    }

    private void archiveClicked(ToolbarMenuSubItem item) {
        openArchive();
    }

    private void orderClicked(ToolbarMenuSubItem item) {
        handleSorting(threadLayout.getPresenter());
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        handleShareAndOpenInBrowser(threadLayout.getPresenter(), false);
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        handleShareAndOpenInBrowser(threadLayout.getPresenter(), true);
    }

    private void setupMiddleNavigation() {
        navigation.setMiddleMenu(anchor -> {
            BrowseBoardsFloatingMenu boardsFloatingMenu = new BrowseBoardsFloatingMenu(context);
            boardsFloatingMenu.show(view, anchor, BrowseController.this,
                    presenter.currentBoard());
        });
    }

    @Override
    public void onBoardClicked(Board item) {
        presenter.onBoardsFloatingMenuBoardClicked(item);
    }

    @Override
    public void onSiteClicked(Site site) {
        presenter.onBoardsFloatingMenuSiteClicked(site);
    }

    private void openArchive() {
        Board board = presenter.currentBoard();
        if (board == null) {
            return;
        }

        ArchiveController archiveController = new ArchiveController(context);
        archiveController.setBoard(board);

        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(archiveController);
        } else {
            navigationController.pushController(archiveController);
        }
    }

    private void handleShareAndOpenInBrowser(ThreadPresenter presenter, boolean share) {
        if (presenter.isBound()) {
            Loadable loadable = presenter.getLoadable();
            String link = loadable.site.resolvable().desktopUrl(loadable, presenter.getChanThread().op);

            if (share) {
                AndroidUtils.shareLink(link);
            } else {
                AndroidUtils.openLinkInBrowser((Activity) context, link);
            }
        }
    }

    private void handleViewMode(ToolbarMenuSubItem item) {
        if (postViewMode == ChanSettings.PostViewMode.LIST) {
            postViewMode = ChanSettings.PostViewMode.CARD;
        } else {
            postViewMode = ChanSettings.PostViewMode.LIST;
        }

        ChanSettings.boardViewMode.set(postViewMode);

        int viewModeText = postViewMode == ChanSettings.PostViewMode.LIST ?
                R.string.action_switch_catalog : R.string.action_switch_board;
        item.text = context.getString(viewModeText);

        threadLayout.setPostViewMode(postViewMode);
    }

    private void handleSorting(final ThreadPresenter presenter) {
        List<FloatingMenuItem> items = new ArrayList<>();
        for (PostsFilter.Order order : PostsFilter.Order.values()) {
            int nameId = 0;
            switch (order) {
                case BUMP:
                    nameId = R.string.order_bump;
                    break;
                case REPLY:
                    nameId = R.string.order_reply;
                    break;
                case IMAGE:
                    nameId = R.string.order_image;
                    break;
                case NEWEST:
                    nameId = R.string.order_newest;
                    break;
                case OLDEST:
                    nameId = R.string.order_oldest;
                    break;
                case MODIFIED:
                    nameId = R.string.order_modified;
                    break;
            }

            String name = getString(nameId);
            if (order == this.order) {
                name = "\u2713 " + name; // Checkmark
            }

            items.add(new FloatingMenuItem(order, name));
        }

        ToolbarMenuItem overflow = navigation.findItem(ToolbarMenu.OVERFLOW_ID);
        FloatingMenu menu = new FloatingMenu(context, overflow.getView(), items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                PostsFilter.Order order = (PostsFilter.Order) item.getId();
                ChanSettings.boardOrder.set(order.name);
                BrowseController.this.order = order;
                presenter.setOrder(order);
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    @Override
    public void loadBoard(Loadable loadable) {
        String name = BoardHelper.getName(loadable.board);
        loadable.title = name;
        navigation.title = name;

        ThreadPresenter presenter = threadLayout.getPresenter();
        presenter.unbindLoadable();
        presenter.bindLoadable(loadable);
        presenter.requestData();

        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
    }

    @Override
    public void loadSiteSetup(Site site) {
        SiteSetupController siteSetupController = new SiteSetupController(context);
        siteSetupController.setSite(site);

        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(siteSetupController);
        } else {
            navigationController.pushController(siteSetupController);
        }
    }

    @Override
    public void showArchiveOption(boolean show) {
        ToolbarMenuSubItem archive = navigation.findSubItem(ARCHIVE_ID);
        archive.enabled = show;
    }

    @Override
    public void openPin(Pin pin) {
        showThread(pin.loadable);
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        showThread(threadLoadable, true);
    }

    @Override
    public void showBoard(Loadable catalogLoadable) {
        //we don't actually need to do anything here because you can't tap board links in the browse controller
        //set the board just in case?
        setBoard(catalogLoadable.board);
    }

    // Creates or updates the target ThreadViewController
    // This controller can be in various places depending on the layout
    // We dynamically search for it
    public void showThread(Loadable threadLoadable, boolean animated) {
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
                StyledToolbarNavigationController navigationController = (StyledToolbarNavigationController) splitNav.getRightController();

                if (navigationController.getTop() instanceof ViewThreadController) {
                    ((ViewThreadController) navigationController.getTop()).loadThread(threadLoadable);
                }
            } else {
                StyledToolbarNavigationController navigationController = new StyledToolbarNavigationController(context);
                splitNav.setRightController(navigationController);
                ViewThreadController viewThreadController = new ViewThreadController(context);
                viewThreadController.setLoadable(threadLoadable);
                navigationController.pushController(viewThreadController, false);
            }
            splitNav.switchToController(false);
        } else if (slideNav != null) {
            // Create a threadview in the right part of the slide nav *without* a toolbar
            if (slideNav.getRightController() instanceof ViewThreadController) {
                ((ViewThreadController) slideNav.getRightController()).loadThread(threadLoadable);
            } else {
                ViewThreadController viewThreadController = new ViewThreadController(context);
                viewThreadController.setLoadable(threadLoadable);
                slideNav.setRightController(viewThreadController);
            }
            slideNav.switchToController(false);
        } else {
            // the target ThreadNav must be pushed to the parent nav controller
            // (BrowseController -> ToolbarNavigationController)
            ViewThreadController viewThreadController = new ViewThreadController(context);
            viewThreadController.setLoadable(threadLoadable);
            navigationController.pushController(viewThreadController, animated);
        }
    }
}
