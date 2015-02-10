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

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.toolbar.ToolbarMenuSubItem;
import org.floens.chan.ui.toolbar.ToolbarMenuSubMenu;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

public class BrowseController extends Controller implements ToolbarMenuItem.ToolbarMenuItemCallback, ThreadLayout.ThreadLayoutCallback, ToolbarMenuSubMenu.ToolbarMenuItemSubMenuCallback, BoardManager.BoardChangeListener {
    private static final int REFRESH_ID = 1;
    private static final int POST_ID = 2;
    private static final int SEARCH_ID = 101;
    private static final int SHARE_ID = 102;
    private static final int SETTINGS_ID = 103;

    private ThreadLayout threadLayout;
    private List<ToolbarMenuSubItem> boardItems;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ChanApplication.getBoardManager().addListener(this);

        navigationItem.middleMenu = new ToolbarMenuSubMenu(context);
        navigationItem.middleMenu.setCallback(this);
        loadBoards();

        navigationItem.title = "Hello world";
        ToolbarMenu menu = new ToolbarMenu(context);
        navigationItem.menu = menu;
        navigationItem.hasBack = false;

        menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_action_refresh));
        menu.addItem(new ToolbarMenuItem(context, this, POST_ID, R.drawable.ic_action_write));

        ToolbarMenuItem overflow = menu.createOverflow(this);

        List<ToolbarMenuSubItem> items = new ArrayList<>();
        items.add(new ToolbarMenuSubItem(SEARCH_ID, context.getString(R.string.action_search)));
        items.add(new ToolbarMenuSubItem(SHARE_ID, context.getString(R.string.action_share)));
        items.add(new ToolbarMenuSubItem(SETTINGS_ID, context.getString(R.string.action_settings)));

        overflow.setSubMenu(new ToolbarMenuSubMenu(context, overflow.getView(), items));

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);

        view = threadLayout;

        loadBoard(ChanApplication.getBoardManager().getSavedBoards().get(0));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ChanApplication.getBoardManager().removeListener(this);
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch (item.getId()) {
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                break;
            case POST_ID:
                // TODO
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, ToolbarMenuSubItem item) {
        switch (item.getId()) {
            case SEARCH_ID:
                // TODO
                break;
            case SHARE_ID:
                String link = ChanUrls.getCatalogUrlDesktop(threadLayout.getPresenter().getLoadable().board);
                AndroidUtils.shareLink(link);
                break;
            case SETTINGS_ID:
                SettingsController settingsController = new SettingsController(context);
                navigationController.pushController(settingsController);
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuSubMenu menu, ToolbarMenuSubItem item) {
        if (menu == navigationItem.middleMenu) {
            if (item instanceof ToolbarMenuSubItemBoard) {
                loadBoard(((ToolbarMenuSubItemBoard) item).board);
                navigationController.toolbar.updateNavigation();
            } else {
                // TODO start board editor
            }
        }
    }

    @Override
    public void openThread(Loadable threadLoadable) {
        ViewThreadController viewThreadController = new ViewThreadController(context);
        viewThreadController.setLoadable(threadLoadable);
        navigationController.pushController(viewThreadController);
    }

    @Override
    public void onBoardsChanged() {
        loadBoards();
    }

    private void loadBoard(Board board) {
        Loadable loadable = new Loadable(board.value);
        loadable.mode = Loadable.Mode.CATALOG;
        loadable.generateTitle();
        navigationItem.title = board.key;

        threadLayout.getPresenter().unbindLoadable();
        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();

        for (ToolbarMenuSubItem item : boardItems) {
            if (((ToolbarMenuSubItemBoard) item).board == board) {
                navigationItem.middleMenu.setSelectedItem(item);
            }
        }
    }

    private void loadBoards() {
        List<Board> boards = ChanApplication.getBoardManager().getSavedBoards();
        boardItems = new ArrayList<>();
        for (Board board : boards) {
            ToolbarMenuSubItem item = new ToolbarMenuSubItemBoard(board);
            boardItems.add(item);
        }

        navigationItem.middleMenu.setItems(boardItems);
        navigationItem.middleMenu.setAdapter(new BoardsAdapter(context, boardItems));
    }

    private static class ToolbarMenuSubItemBoard extends ToolbarMenuSubItem {
        public Board board;

        public ToolbarMenuSubItemBoard(Board board) {
            super(board.id, board.key);
            this.board = board;
        }
    }

    private static class BoardsAdapter extends BaseAdapter {
        private final Context context;
        private List<ToolbarMenuSubItem> items;

        public BoardsAdapter(Context context, List<ToolbarMenuSubItem> items) {
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
                return context.getString(R.string.board_select_add);
            }
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
