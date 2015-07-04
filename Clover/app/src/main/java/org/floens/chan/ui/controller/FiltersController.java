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
import org.floens.chan.core.model.Filter;
import org.floens.chan.ui.layout.FilterLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.ui.theme.ThemeHelper.theme;

public class FiltersController extends Controller implements ToolbarMenuItem.ToolbarMenuItemCallback, RootNavigationController.ToolbarSearchCallback, View.OnClickListener {
    private static final int SEARCH_ID = 1;
    private static final int CLEAR_ID = 101;

    private DatabaseManager databaseManager;
    private RecyclerView recyclerView;
    private FloatingActionButton add;
    private FilterAdapter adapter;

    public FiltersController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        databaseManager = Chan.getDatabaseManager();

        navigationItem.title = string(R.string.filters_screen);
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
            navigationController.showSearch();
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
    }

    private void showFilterDialog(Filter filter) {
        final FilterLayout filterLayout = (FilterLayout) LayoutInflater.from(context).inflate(R.layout.layout_filter, null);

        new AlertDialog.Builder(context)
                .setView(filterLayout)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        filterLayout.save();
                        adapter.load();
                    }
                })
                .show();

        filterLayout.setFilter(filter);
    }

    private void deleteFilter(Filter filter) {
        databaseManager.removeFilter(filter);
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
            holder.subtext.setText(String.valueOf(filter.type));
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
                    displayList.add(filter);
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
