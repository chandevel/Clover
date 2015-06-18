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
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
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
    private static final int SEARCH_ID = 101;
    private static final int SHARE_ID = 102;
    private static final int VIEW_MODE_ID = 103;
    private static final int ORDER_ID = 104;

    private PostCellInterface.PostViewMode postViewMode;
    private ThreadPresenter.Order order;
    private List<FloatingMenuItem> boardItems;

    private FloatingMenuItem viewModeMenuItem;
    private ToolbarMenuItem overflow;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        postViewMode = PostCellInterface.PostViewMode.find(ChanSettings.boardViewMode.get());
        order = ThreadPresenter.Order.find(ChanSettings.boardOrder.get());
        threadLayout.setPostViewMode(postViewMode);
        threadLayout.getPresenter().setOrder(order);

        navigationItem.hasDrawer = true;
        navigationItem.middleMenu = new FloatingMenu(context);
        navigationItem.middleMenu.setCallback(this);
        loadBoards();

        ToolbarMenu menu = new ToolbarMenu(context);
        navigationItem.menu = menu;
        navigationItem.hasBack = false;

        menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_refresh_white_24dp));

        overflow = menu.createOverflow(this);

        List<FloatingMenuItem> items = new ArrayList<>();
        items.add(new FloatingMenuItem(SEARCH_ID, context.getString(R.string.action_search)));
        items.add(new FloatingMenuItem(SHARE_ID, context.getString(R.string.action_share)));
        viewModeMenuItem = new FloatingMenuItem(VIEW_MODE_ID, context.getString(
                postViewMode == PostCellInterface.PostViewMode.LIST ? R.string.action_switch_catalog : R.string.action_switch_board));
        items.add(viewModeMenuItem);
        items.add(new FloatingMenuItem(ORDER_ID, context.getString(R.string.action_order)));

        overflow.setSubMenu(new FloatingMenu(context, overflow.getView(), items));
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch ((Integer) item.getId()) {
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        switch ((Integer) item.getId()) {
            case SEARCH_ID:
                navigationController.showSearch();
                break;
            case SHARE_ID:
                String link = ChanUrls.getCatalogUrlDesktop(threadLayout.getPresenter().getLoadable().board);
                AndroidUtils.shareLink(link);
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
                for (ThreadPresenter.Order order : ThreadPresenter.Order.values()) {
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
                        ThreadPresenter.Order order = (ThreadPresenter.Order) item.getId();
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
                navigationController.toolbar.updateNavigation();
            } else {
                navigationController.pushController(new BoardEditController(context));
                menu.dismiss();
            }
        }
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        showThread(threadLoadable, true);
    }

    public void showThread(Loadable threadLoadable, boolean animated) {
        ViewThreadController viewThreadController = new ViewThreadController(context);
        viewThreadController.setLoadable(threadLoadable);
        navigationController.pushController(viewThreadController, animated);
    }

    public void onEvent(BoardManager.BoardsChangedMessage event) {
        loadBoards();
    }

    @Override
    public void onPinClicked(Pin pin) {
        showThread(pin.loadable);
    }

    @Override
    public boolean isPinCurrent(Pin pin) {
        return false;
    }

    public void loadBoard(Board board) {
        Loadable loadable = new Loadable(board.value);
        loadable.mode = Loadable.Mode.CATALOG;
        loadable.title = board.key;
        navigationItem.title = board.key;

        threadLayout.getPresenter().unbindLoadable();
        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();

        for (FloatingMenuItem item : boardItems) {
            if (((FloatingMenuItemBoard) item).board == board) {
                navigationItem.middleMenu.setSelectedItem(item);
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
