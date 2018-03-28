/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.controller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import org.floens.chan.R;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.presenter.BrowsePresenter;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.Site;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.layout.BrowseBoardsFloatingMenu;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.toolbar.ToolbarMiddleMenu;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.getString;

public class BrowseController extends ThreadController implements
        ToolbarMenuItem.ToolbarMenuItemCallback,
        ThreadLayout.ThreadLayoutCallback,
        BrowsePresenter.Callback,
        BrowseBoardsFloatingMenu.ClickCallback {
    private static final int SEARCH_ID = 1;
    private static final int REFRESH_ID = 2;
    private static final int REPLY_ID = 101;
    private static final int SHARE_ID = 103;
    private static final int VIEW_MODE_ID = 104;
    private static final int ORDER_ID = 105;
    private static final int OPEN_BROWSER_ID = 106;
    private static final int ARCHIVE_ID = 107;

    @Inject
    BrowsePresenter presenter;

    private ChanSettings.PostViewMode postViewMode;
    private PostsFilter.Order order;

    private FloatingMenuItem viewModeMenuItem;
    private ToolbarMenuItem search;
    private ToolbarMenuItem refresh;
    private ToolbarMenuItem overflow;
    private FloatingMenuItem archive;

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
        ToolbarMenu menu = new ToolbarMenu(context);
        navigation.menu = menu;
        navigation.hasBack = false;

        search = menu.addItem(new ToolbarMenuItem(context, this, SEARCH_ID, R.drawable.ic_search_white_24dp));
        refresh = menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_refresh_white_24dp));

        // Toolbar overflow
        overflow = menu.createOverflow(this);

        List<FloatingMenuItem> items = new ArrayList<>();
        if (!ChanSettings.enableReplyFab.get()) {
            items.add(new FloatingMenuItem(REPLY_ID, R.string.action_reply));
        }
        viewModeMenuItem = new FloatingMenuItem(VIEW_MODE_ID, postViewMode == ChanSettings.PostViewMode.LIST ?
                R.string.action_switch_catalog : R.string.action_switch_board);
        items.add(viewModeMenuItem);

        archive = new FloatingMenuItem(ARCHIVE_ID, R.string.thread_view_archive);
        items.add(archive);
        archive.setEnabled(false);

        items.add(new FloatingMenuItem(ORDER_ID, R.string.action_order));
        items.add(new FloatingMenuItem(OPEN_BROWSER_ID, R.string.action_open_browser));
        items.add(new FloatingMenuItem(SHARE_ID, R.string.action_share));

        overflow.setSubMenu(new FloatingMenu(context, overflow.getView(), items));

        // Presenter
        presenter.create(this);
    }

    private void setupMiddleNavigation() {
        navigation.middleMenu = new ToolbarMiddleMenu() {
            @SuppressLint("InflateParams")
            @Override
            public void show(View anchor) {
                BrowseBoardsFloatingMenu boardsFloatingMenu = new BrowseBoardsFloatingMenu(context);
                boardsFloatingMenu.show(view, anchor, BrowseController.this,
                        presenter.currentBoard());
            }
        };
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        ThreadPresenter presenter = threadLayout.getPresenter();

        switch ((Integer) item.getId()) {
            case SEARCH_ID:
                if (presenter.isBound()) {
                    ((ToolbarNavigationController) navigationController).showSearch();
                }
                break;
            case REFRESH_ID:
                if (presenter.isBound()) {
                    presenter.requestData();
                    ImageView refreshView = refresh.getView();
                    refreshView.setRotation(0f);
                    refreshView.animate()
                            .rotation(360f)
                            .setDuration(500)
                            .setInterpolator(new DecelerateInterpolator(2f));
                }
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        final ThreadPresenter presenter = threadLayout.getPresenter();

        Integer id = (Integer) item.getId();
        switch (id) {
            case REPLY_ID:
                handleReply();
                break;
            case SHARE_ID:
            case OPEN_BROWSER_ID:
                handleShareAndOpenInBrowser(presenter, id);
                break;
            case VIEW_MODE_ID:
                handleViewMode();

                break;
            case ORDER_ID:
                handleOrder(presenter);

                break;
            case ARCHIVE_ID:
                openArchive();
                break;
        }
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

    private void handleReply() {
        threadLayout.openReply(true);
    }

    private void handleShareAndOpenInBrowser(ThreadPresenter presenter, Integer id) {
        if (presenter.isBound()) {
            Loadable loadable = presenter.getLoadable();
            String link = loadable.site.resolvable().desktopUrl(loadable, null);

            if (id == SHARE_ID) {
                AndroidUtils.shareLink(link);
            } else {
                AndroidUtils.openLinkInBrowser((Activity) context, link);
            }
        }
    }

    private void handleViewMode() {
        if (postViewMode == ChanSettings.PostViewMode.LIST) {
            postViewMode = ChanSettings.PostViewMode.CARD;
        } else {
            postViewMode = ChanSettings.PostViewMode.LIST;
        }

        ChanSettings.boardViewMode.set(postViewMode);

        int viewModeText = postViewMode == ChanSettings.PostViewMode.LIST ?
                R.string.action_switch_catalog : R.string.action_switch_board;
        viewModeMenuItem.setText(context.getString(viewModeText));

        threadLayout.setPostViewMode(postViewMode);
    }

    private void handleOrder(final ThreadPresenter presenter) {
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
            }

            String name = getString(nameId);
            if (order == this.order) {
                name = "\u2713 " + name; // Checkmark
            }

            items.add(new FloatingMenuItem(order, name));
        }

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
        archive.setEnabled(show);
    }

    @Override
    public void openPin(Pin pin) {
        showThread(pin.loadable);
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        showThread(threadLoadable, true);
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
