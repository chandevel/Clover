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

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.database.DatabaseHistoryManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.DatabaseSavedReplyManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.History;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.HintPopup;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;

public class HistoryController extends Controller implements CompoundButton.OnCheckedChangeListener, ToolbarMenuItem.ToolbarMenuItemCallback, ToolbarNavigationController.ToolbarSearchCallback {
    private static final int SEARCH_ID = 1;
    private static final int CLEAR_ID = 101;
    private static final int SAVED_REPLY_CLEAR_ID = 102;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    BoardManager boardManager;

    private DatabaseHistoryManager databaseHistoryManager;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private CrossfadeView crossfade;
    private RecyclerView recyclerView;
    private HistoryAdapter adapter;

    public HistoryController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getGraph().inject(this);

        databaseHistoryManager = databaseManager.getDatabaseHistoryManager();
        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();

        navigationItem.setTitle(R.string.history_screen);
        List<FloatingMenuItem> items = new ArrayList<>();
        items.add(new FloatingMenuItem(CLEAR_ID, R.string.history_clear));
        items.add(new FloatingMenuItem(SAVED_REPLY_CLEAR_ID, R.string.saved_reply_clear));
        navigationItem.menu = new ToolbarMenu(context);
        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, SEARCH_ID, R.drawable.ic_search_white_24dp));
        ToolbarMenuItem overflow = navigationItem.createOverflow(context, this, items);
        overflow.getSubMenu().setPopupWidth(dp(4 * 56));

        SwitchCompat historyEnabledSwitch = new SwitchCompat(context);
        historyEnabledSwitch.setChecked(ChanSettings.historyEnabled.get());
        historyEnabledSwitch.setOnCheckedChangeListener(this);
        navigationItem.rightView = historyEnabledSwitch;

        view = inflateRes(R.layout.controller_history);
        crossfade = (CrossfadeView) view.findViewById(R.id.crossfade);
        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
        adapter.load();

        if (ChanSettings.historyOpenCounter.increase() == 1) {
            HintPopup.show(context, historyEnabledSwitch, R.string.history_toggle_hint);
        }
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        if ((Integer) item.getId() == SEARCH_ID) {
            ((ToolbarNavigationController) navigationController).showSearch();
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        switch ((Integer) item.getId()) {
            case CLEAR_ID:
                new AlertDialog.Builder(context)
                        .setTitle(R.string.history_clear_confirm)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.history_clear_confirm_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                databaseManager.runTaskAsync(databaseHistoryManager.clearHistory());
                                adapter.load();
                            }
                        })
                        .show();
                break;
            case SAVED_REPLY_CLEAR_ID:
                new AlertDialog.Builder(context)
                        .setTitle(R.string.saved_reply_clear_confirm)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.saved_reply_clear_confirm_button, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                databaseManager.runTaskAsync(databaseSavedReplyManager.clearSavedReplies());
                            }
                        })
                        .show();
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        ChanSettings.historyEnabled.set(isChecked);
    }

    private void openThread(History history) {
        ViewThreadController viewThreadController = new ViewThreadController(context);
        viewThreadController.setLoadable(history.loadable);
        navigationController.pushController(viewThreadController);
    }

    private void deleteHistory(History history) {
        databaseManager.runTask(databaseHistoryManager.removeHistory(history));
        adapter.load();
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            adapter.search(null);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.search(entered);
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryCell> implements DatabaseManager.TaskResult<List<History>> {
        private List<History> sourceList = new ArrayList<>();
        private List<History> displayList = new ArrayList<>();
        private String searchQuery;

        private boolean resultPending = false;

        public HistoryAdapter() {
            setHasStableIds(true);
        }

        @Override
        public HistoryCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new HistoryCell(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_history, parent, false));
        }

        @Override
        public void onBindViewHolder(HistoryCell holder, int position) {
            History history = displayList.get(position);
            holder.thumbnail.setUrl(history.thumbnailUrl, dp(48), dp(48));

            holder.text.setText(history.loadable.title);
            Board board = history.loadable.board;
            holder.subtext.setText(board == null ? null : ("/" + board.code + "/ \u2013 " + board.name));
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).id;
        }

        public void search(String query) {
            this.searchQuery = query;
            filter();
        }

        private void load() {
            if (!resultPending) {
                resultPending = true;
                databaseManager.runTaskAsync(databaseHistoryManager.getHistory(), this);
            }
        }

        @Override
        public void onComplete(List<History> result) {
            resultPending = false;
            sourceList.clear();
            sourceList.addAll(result);
            crossfade.toggle(!sourceList.isEmpty(), true);
            filter();
        }

        private void filter() {
            displayList.clear();
            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (History history : sourceList) {
                    if (history.loadable.title.toLowerCase(Locale.ENGLISH).contains(query)) {
                        displayList.add(history);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
        }
    }

    private class HistoryCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ThumbnailView thumbnail;
        private TextView text;
        private TextView subtext;
        private ImageView delete;

        public HistoryCell(View itemView) {
            super(itemView);

            thumbnail = (ThumbnailView) itemView.findViewById(R.id.thumbnail);
            thumbnail.setCircular(true);
            text = (TextView) itemView.findViewById(R.id.text);
            subtext = (TextView) itemView.findViewById(R.id.subtext);
            delete = (ImageView) itemView.findViewById(R.id.delete);

            theme().clearDrawable.apply(delete);

            delete.setOnClickListener(this);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position >= 0 && position < adapter.getItemCount()) {
                History history = adapter.displayList.get(position);
                if (v == itemView) {
                    openThread(history);
                } else if (v == delete) {
                    deleteHistory(history);
                }
            }

        }
    }
}
