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
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Filter;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.layout.FilterLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.greenrobot.event.EventBus;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class FiltersController extends Controller implements ToolbarMenuItem.ToolbarMenuItemCallback, ToolbarNavigationController.ToolbarSearchCallback, View.OnClickListener {
    private static final int SEARCH_ID = 1;
    private static final int CLEAR_ID = 101;

    private FilterEngine filterEngine;
    private DatabaseManager databaseManager;
    private RecyclerView recyclerView;
    private FloatingActionButton add;
    private FilterAdapter adapter;

    public FiltersController(Context context) {
        super(context);
    }

    public static String filterTypeName(FilterEngine.FilterType type) {
        switch (type) {
            case TRIPCODE:
                return getString(R.string.filter_tripcode);
            case NAME:
                return getString(R.string.filter_name);
            case COMMENT:
                return getString(R.string.filter_comment);
            case ID:
                return getString(R.string.filter_id);
            case SUBJECT:
                return getString(R.string.filter_subject);
            case FILENAME:
                return getString(R.string.filter_filename);
        }
        return null;
    }

    public static String actionName(FilterEngine.FilterAction action) {
        switch (action) {
            case HIDE:
                return getString(R.string.filter_hide);
            case COLOR:
                return getString(R.string.filter_color);
            case REMOVE:
                return getString(R.string.filter_remove);
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        filterEngine = FilterEngine.getInstance();
        databaseManager = Chan.getDatabaseManager();

        navigationItem.setTitle(R.string.filters_screen);
        navigationItem.menu = new ToolbarMenu(context);
        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, SEARCH_ID, R.drawable.ic_search_white_24dp));

        view = inflateRes(R.layout.controller_filters);

        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        add = (FloatingActionButton) view.findViewById(R.id.add);
        add.setOnClickListener(this);

        adapter = new FilterAdapter();
        recyclerView.setAdapter(adapter);
        adapter.load();
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            showFilterDialog(new Filter());
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
    }

    public void showFilterDialog(final Filter filter) {
        final FilterLayout filterLayout = (FilterLayout) LayoutInflater.from(context).inflate(R.layout.layout_filter, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(filterLayout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Filter newFilter = filterLayout.getFilter();
                        newFilter.id = filter.id;
                        filterEngine.addOrUpdate(newFilter);
                        EventBus.getDefault().post(new RefreshUIMessage("filters"));
                        adapter.load();
                    }
                })
                .show();

        filterLayout.setCallback(new FilterLayout.FilterLayoutCallback() {
            @Override
            public void setSaveButtonEnabled(boolean enabled) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled);
            }
        });
        filterLayout.setFilter(filter);
    }

    private void deleteFilter(Filter filter) {
        filterEngine.remove(filter);
        EventBus.getDefault().post(new RefreshUIMessage("filters"));
        adapter.load();
        //TODO: undo
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

    private class FilterAdapter extends RecyclerView.Adapter<FilterCell> {
        private List<Filter> sourceList = new ArrayList<>();
        private List<Filter> displayList = new ArrayList<>();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
        }

        @Override
        public FilterCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new FilterCell(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_filter, parent, false));
        }

        @Override
        public void onBindViewHolder(FilterCell holder, int position) {
            Filter filter = displayList.get(position);
            holder.text.setText(filter.pattern);
            holder.text.setTextColor(getAttrColor(context, filter.enabled ? R.attr.text_color_primary : R.attr.text_color_hint));
            holder.subtext.setTextColor(getAttrColor(context, filter.enabled ? R.attr.text_color_secondary : R.attr.text_color_hint));
            String subText = filterTypeName(FilterEngine.FilterType.forId(filter.type));

            subText += " - ";
            if (filter.allBoards) {
                subText += context.getString(R.string.filter_summary_all_boards);
            } else {
                int size = filterEngine.getBoardsForFilter(filter).size();
                subText += context.getResources().getQuantityString(R.plurals.board, size, size);
            }

            subText += " - " + FiltersController.actionName(FilterEngine.FilterAction.forId(filter.action));

            holder.subtext.setText(subText);
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
            sourceList.clear();
            sourceList.addAll(databaseManager.getFilters());

            filter();
        }

        private void filter() {
            displayList.clear();
            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (Filter filter : sourceList) {
                    if (filter.pattern.toLowerCase().contains(query)) {
                        displayList.add(filter);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
        }
    }

    private class FilterCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView text;
        private TextView subtext;
        private ImageView delete;

        public FilterCell(View itemView) {
            super(itemView);

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
                Filter filter = adapter.displayList.get(position);
                if (v == itemView) {
                    showFilterDialog(filter);
                } else if (v == delete) {
                    deleteFilter(filter);
                }
            }

        }
    }
}
