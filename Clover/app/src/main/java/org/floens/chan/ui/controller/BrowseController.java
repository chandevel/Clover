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
import android.app.ProgressDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getString;

public class BrowseController extends ThreadController implements ToolbarMenuItem.ToolbarMenuItemCallback, ThreadLayout.ThreadLayoutCallback, FloatingMenu.FloatingMenuCallback {
    private static final int SEARCH_ID = 1;
    private static final int REFRESH_ID = 2;
    private static final int REPLY_ID = 101;
    private static final int SHARE_ID = 103;
    private static final int VIEW_MODE_ID = 104;
    private static final int ORDER_ID = 105;
    private static final int OPEN_BROWSER_ID = 106;

    private final DatabaseManager databaseManager;

    private ChanSettings.PostViewMode postViewMode;
    private PostsFilter.Order order;
    private List<FloatingMenuItem> boardItems;

    private ProgressDialog waitingForBoardsDialog;

    private FloatingMenuItem viewModeMenuItem;
    private ToolbarMenuItem search;
    private ToolbarMenuItem refresh;
    private ToolbarMenuItem overflow;

    public BrowseController(Context context) {
        super(context);
        databaseManager = Chan.getDatabaseManager();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        postViewMode = ChanSettings.boardViewMode.get();
        order = PostsFilter.Order.find(ChanSettings.boardOrder.get());
        threadLayout.setPostViewMode(postViewMode);
        threadLayout.getPresenter().setOrder(order);

        navigationItem.hasDrawer = true;
        navigationItem.middleMenu = new FloatingMenu(context);
        navigationItem.middleMenu.setCallback(this);
        loadBoards();

        ToolbarMenu menu = new ToolbarMenu(context);
        navigationItem.menu = menu;
        navigationItem.hasBack = false;

        search = menu.addItem(new ToolbarMenuItem(context, this, SEARCH_ID, R.drawable.ic_search_white_24dp));
        refresh = menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_refresh_white_24dp));

        overflow = menu.createOverflow(this);

        List<FloatingMenuItem> items = new ArrayList<>();
        if (!ChanSettings.enableReplyFab.get()) {
            items.add(new FloatingMenuItem(REPLY_ID, R.string.action_reply));
        }
        items.add(new FloatingMenuItem(SHARE_ID, R.string.action_share));
        viewModeMenuItem = new FloatingMenuItem(VIEW_MODE_ID, postViewMode == ChanSettings.PostViewMode.LIST ?
                R.string.action_switch_catalog : R.string.action_switch_board);
        items.add(viewModeMenuItem);
        items.add(new FloatingMenuItem(ORDER_ID, R.string.action_order));
        items.add(new FloatingMenuItem(OPEN_BROWSER_ID, R.string.action_open_browser));

        overflow.setSubMenu(new FloatingMenu(context, overflow.getView(), items));
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch ((Integer) item.getId()) {
            case SEARCH_ID:
                ((ToolbarNavigationController) navigationController).showSearch();
                break;
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                refresh.getView().setRotation(0f);
                refresh.getView().animate().rotation(360f).setDuration(500).setInterpolator(new DecelerateInterpolator(2f));
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        Integer id = (Integer) item.getId();
        switch (id) {
            case REPLY_ID:
                threadLayout.openReply(true);
                break;
            case SHARE_ID:
            case OPEN_BROWSER_ID:
                String link = ChanUrls.getCatalogUrlDesktop(threadLayout.getPresenter().getLoadable().boardCode);

                if (id == SHARE_ID) {
                    AndroidUtils.shareLink(link);
                } else {
                    AndroidUtils.openLinkInBrowser((Activity) context, link);
                }

                break;
            case VIEW_MODE_ID:
                if (postViewMode == ChanSettings.PostViewMode.LIST) {
                    postViewMode = ChanSettings.PostViewMode.CARD;
                } else {
                    postViewMode = ChanSettings.PostViewMode.LIST;
                }

                ChanSettings.boardViewMode.set(postViewMode);

                viewModeMenuItem.setText(context.getString(
                        postViewMode == ChanSettings.PostViewMode.LIST ? R.string.action_switch_catalog : R.string.action_switch_board));

                threadLayout.setPostViewMode(postViewMode);

                break;
            case ORDER_ID:
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
                        threadLayout.getPresenter().setOrder(order);
                    }

                    @Override
                    public void onFloatingMenuDismissed(FloatingMenu menu) {
                    }
                });
                menu.show();

                break;
        }
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        if (menu == navigationItem.middleMenu) {
            if (item instanceof FloatingMenuItemBoard) {
                loadBoard(((FloatingMenuItemBoard) item).board);
            } else {
                BoardEditController boardEditController = new BoardEditController(context);
                if (doubleNavigationController != null) {
                    doubleNavigationController.pushController(boardEditController);
                } else {
                    navigationController.pushController(boardEditController);
                }
                menu.dismiss();
            }
        }
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
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

    public void onEvent(BoardManager.BoardsChangedMessage event) {
        loadBoards();
    }

    public void loadDefault() {
        BoardManager boardManager = Chan.getBoardManager();
        List<Board> savedBoards = boardManager.getSavedBoards();
        if (!savedBoards.isEmpty()) {
            loadBoard(savedBoards.get(0));
        }
    }

    public void loadBoard(Board board) {
        Loadable loadable = databaseManager.getDatabaseLoadableManager().get(Loadable.forCatalog(board));
        loadable.title = board.getName();
        navigationItem.title = board.getName();

        ThreadPresenter presenter = threadLayout.getPresenter();
        presenter.unbindLoadable();
        presenter.bindLoadable(loadable);
        presenter.requestData();

        for (FloatingMenuItem item : boardItems) {
            if (((FloatingMenuItemBoard) item).board == board) {
                navigationItem.middleMenu.setSelectedItem(item);
                break;
            }
        }
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigationItem);
    }

    /**
     * Load the menu with saved boards. Called on creation of this controller and when there's a
     * board change event.
     */
    private void loadBoards() {
        List<Board> boards = Chan.getBoardManager().getSavedBoards();

        if (boards.isEmpty()) {
            if (waitingForBoardsDialog == null) {
                String title = getString(R.string.thread_fetching_boards_title);
                String message = getString(R.string.thread_fetching_boards);
                waitingForBoardsDialog = ProgressDialog.show(context, title, message, true, false);
                waitingForBoardsDialog.show();
            }
        } else {
            boolean wasWaiting = waitingForBoardsDialog != null;
            if (waitingForBoardsDialog != null) {
                waitingForBoardsDialog.dismiss();
                waitingForBoardsDialog = null;
            }

            boardItems = new ArrayList<>();
            for (Board board : boards) {
                FloatingMenuItem item = new FloatingMenuItemBoard(board);
                boardItems.add(item);
            }

            navigationItem.middleMenu.setItems(boardItems);
            navigationItem.middleMenu.setAdapter(new BoardsAdapter(context, boardItems));

            if (wasWaiting) {
                loadDefault();
            }
        }
    }

    private static class FloatingMenuItemBoard extends FloatingMenuItem {
        public Board board;

        public FloatingMenuItemBoard(Board board) {
            super(board.id, board.getName());
            this.board = board;
        }
    }

    private static class BoardsAdapter extends BaseAdapter {
        private final Context context;
        private List<FloatingMenuItem> items;

        public BoardsAdapter(Context context, List<FloatingMenuItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // No recycling, can't use itemtypes
            @SuppressLint("ViewHolder")
            TextView textView = (TextView) LayoutInflater.from(context).inflate(R.layout.toolbar_menu_item, parent, false);
            textView.setText(getItem(position));
            if (position < items.size()) {
                textView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
            } else {
                textView.setTypeface(AndroidUtils.ROBOTO_MEDIUM_ITALIC);
            }

            return textView;
        }

        @Override
        public int getCount() {
            return items.size() + 1;
        }

        @Override
        public String getItem(int position) {
            if (position >= 0 && position < items.size()) {
                return items.get(position).getText();
            } else {
                return context.getString(R.string.thread_board_select_add);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
