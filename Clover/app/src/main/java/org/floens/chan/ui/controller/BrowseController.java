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
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.pool.LoadablePool;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

public class BrowseController extends ThreadController implements ToolbarMenuItem.ToolbarMenuItemCallback, ThreadLayout.ThreadLayoutCallback, FloatingMenu.FloatingMenuCallback {
    private static final int REFRESH_ID = 1;
    private static final int REPLY_ID = 101;
    private static final int SEARCH_ID = 102;
    private static final int SHARE_ID = 103;
    private static final int VIEW_MODE_ID = 104;
    private static final int ORDER_ID = 105;
    private static final int OPEN_BROWSER_ID = 106;

    private PostCellInterface.PostViewMode postViewMode;
    private PostsFilter.Order order;
    private List<FloatingMenuItem> boardItems;

    private FloatingMenuItem viewModeMenuItem;
    private ToolbarMenuItem overflow;
    private ToolbarMenuItem refresh;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        postViewMode = PostCellInterface.PostViewMode.find(ChanSettings.boardViewMode.get());
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

        refresh = menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_refresh_white_24dp));

        overflow = menu.createOverflow(this);

        List<FloatingMenuItem> items = new ArrayList<>();
        if (!ChanSettings.enableReplyFab.get()) {
            items.add(new FloatingMenuItem(REPLY_ID, context.getString(R.string.action_reply)));
        }
        items.add(new FloatingMenuItem(SEARCH_ID, context.getString(R.string.action_search)));
        items.add(new FloatingMenuItem(SHARE_ID, context.getString(R.string.action_share)));
        viewModeMenuItem = new FloatingMenuItem(VIEW_MODE_ID, context.getString(
                postViewMode == PostCellInterface.PostViewMode.LIST ? R.string.action_switch_catalog : R.string.action_switch_board));
        items.add(viewModeMenuItem);
        items.add(new FloatingMenuItem(ORDER_ID, context.getString(R.string.action_order)));
        items.add(new FloatingMenuItem(OPEN_BROWSER_ID, context.getString(R.string.action_open_browser)));

        overflow.setSubMenu(new FloatingMenu(context, overflow.getView(), items));
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch ((Integer) item.getId()) {
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
            case SEARCH_ID:
                ((ToolbarNavigationController) navigationController).showSearch();
                break;
            case SHARE_ID:
            case OPEN_BROWSER_ID:
                String link = ChanUrls.getCatalogUrlDesktop(threadLayout.getPresenter().getLoadable().board);

                if (id == SHARE_ID) {
                    AndroidUtils.shareLink(link);
                } else {
                    AndroidUtils.openLink(link);
                }

                break;
            case VIEW_MODE_ID:
                if (postViewMode == PostCellInterface.PostViewMode.LIST) {
                    postViewMode = PostCellInterface.PostViewMode.CARD;
                } else {
                    postViewMode = PostCellInterface.PostViewMode.LIST;
                }

                ChanSettings.boardViewMode.set(postViewMode.name);

                viewModeMenuItem.setText(context.getString(
                        postViewMode == PostCellInterface.PostViewMode.LIST ? R.string.action_switch_catalog : R.string.action_switch_board));

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

                    String name = string(nameId);
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
                if (splitNavigationController != null) {
                    splitNavigationController.pushController(boardEditController);
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

    public void showThread(Loadable threadLoadable, boolean animated) {
        if (splitNavigationController != null) {
            if (splitNavigationController.rightController instanceof StyledToolbarNavigationController) {
                StyledToolbarNavigationController navigationController = (StyledToolbarNavigationController) splitNavigationController.rightController;

                if (navigationController.getTop() instanceof ViewThreadController) {
                    ((ViewThreadController) navigationController.getTop()).loadThread(threadLoadable);
                }
            } else {
                StyledToolbarNavigationController navigationController = new StyledToolbarNavigationController(context);
                splitNavigationController.setRightController(navigationController);
                ViewThreadController viewThreadController = new ViewThreadController(context);
                viewThreadController.setLoadable(threadLoadable);
                navigationController.pushController(viewThreadController, false);
            }
        } else {
            ViewThreadController viewThreadController = new ViewThreadController(context);
            viewThreadController.setLoadable(threadLoadable);
            navigationController.pushController(viewThreadController, animated);
        }
    }

    public void onEvent(BoardManager.BoardsChangedMessage event) {
        loadBoards();
    }

    public void loadBoard(Board board) {
        Loadable loadable = LoadablePool.getInstance().obtain(new Loadable(board.value));
        loadable.mode = Loadable.Mode.CATALOG;
        loadable.title = board.key;
        navigationItem.title = board.key;

        threadLayout.getPresenter().unbindLoadable();
        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();

        for (FloatingMenuItem item : boardItems) {
            if (((FloatingMenuItemBoard) item).board == board) {
                navigationItem.middleMenu.setSelectedItem(item);
                break;
            }
        }
        navigationItem.updateTitle();
    }

    private void loadBoards() {
        List<Board> boards = Chan.getBoardManager().getSavedBoards();
        boardItems = new ArrayList<>();
        for (Board board : boards) {
            FloatingMenuItem item = new FloatingMenuItemBoard(board);
            boardItems.add(item);
        }

        navigationItem.middleMenu.setItems(boardItems);
        navigationItem.middleMenu.setAdapter(new BoardsAdapter(context, boardItems));
    }

    private static class FloatingMenuItemBoard extends FloatingMenuItem {
        public Board board;

        public FloatingMenuItemBoard(Board board) {
            super(board.id, board.key);
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
