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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.FilterLayout;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.fixSnackbarText;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class FiltersController extends Controller implements
        ToolbarNavigationController.ToolbarSearchCallback,
        View.OnClickListener {
    @Inject
    DatabaseManager databaseManager;

    @Inject
    FilterEngine filterEngine;

    private RecyclerView recyclerView;
    private FloatingActionButton add;
    private FloatingActionButton enable;
    private FilterAdapter adapter;

    private ItemTouchHelper itemTouchHelper;
    private boolean attached;

    private ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
    ) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || !TextUtils.isEmpty(adapter.searchQuery)) {
                //require that no search is going on while we do the sorting
                return false;
            }

            adapter.move(from, to);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
                int position = viewHolder.getAdapterPosition();
                deleteFilter(adapter.displayList.get(position));
            }
        }
    };

    public FiltersController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        view = inflateRes(R.layout.controller_filters);

        navigation.setTitle(R.string.filters_screen);
        navigation.swipeable = false;
        navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .build();

        adapter = new FilterAdapter();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attached = true;

        add = view.findViewById(R.id.add);
        add.setOnClickListener(this);
        Chan.injector().instance(ThemeHelper.class).getTheme().applyFabColor(add);

        enable = view.findViewById(R.id.enable);
        enable.setOnClickListener(this);
        Chan.injector().instance(ThemeHelper.class).getTheme().applyFabColor(enable);
    }

    @Override
    public void onDestroy() {
        databaseManager.getDatabaseFilterManager().updateFilters(adapter.sourceList);
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            Filter f = new Filter();
            //add to the end of the filter list
            f.order = adapter.getItemCount();
            showFilterDialog(f);
        } else if (v == enable) {
            FloatingActionButton enableButton = (FloatingActionButton) v;
            //if every filter is disabled, enable all of them and set the drawable to be an x
            //if every filter is enabled, disable all of them and set the drawable to be a checkmark
            //if some filters are enabled, disable them and set the drawable to be a checkmark
            List<Filter> enabledFilters = filterEngine.getEnabledFilters();
            List<Filter> allFilters = filterEngine.getAllFilters();
            if (enabledFilters.isEmpty()) {
                setFilters(allFilters, true);
                enableButton.setImageResource(R.drawable.ic_clear_white_24dp);
            } else if (enabledFilters.size() == allFilters.size()) {
                setFilters(allFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            } else {
                setFilters(enabledFilters, false);
                enableButton.setImageResource(R.drawable.ic_done_white_24dp);
            }
            Chan.injector().instance(ThemeHelper.class).getTheme().applyFabColor(enable);
            adapter.reload();
        }
    }

    private void setFilters(List<Filter> filters, boolean enabled) {
        for (Filter filter : filters) {
            filter.enabled = enabled;
            filterEngine.createOrUpdateFilter(filter);
        }
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    public void showFilterDialog(final Filter filter) {
        final FilterLayout filterLayout = (FilterLayout) LayoutInflater.from(context).inflate(R.layout.layout_filter, null);

        final AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(filterLayout)
                .setPositiveButton(R.string.save, (dialog, which) -> {

                    filterEngine.createOrUpdateFilter(filterLayout.getFilter());
                    if (filterEngine.getEnabledFilters().isEmpty()) {
                        enable.setImageResource(R.drawable.ic_done_white_24dp);
                    } else {
                        enable.setImageResource(R.drawable.ic_clear_white_24dp);
                    }
                    Chan.injector().instance(ThemeHelper.class).getTheme().applyFabColor(enable);
                    EventBus.getDefault().post(new RefreshUIMessage("filters"));
                    adapter.reload();
                })
                .show();

        filterLayout.setCallback(enabled -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled));
        filterLayout.setFilter(filter);
    }

    private void deleteFilter(Filter filter) {
        Filter clone = filter.clone();
        filterEngine.deleteFilter(filter);
        EventBus.getDefault().post(new RefreshUIMessage("filters"));
        adapter.reload();

        Snackbar s = Snackbar.make(view, context.getString(R.string.filter_removed_undo, clone.pattern), Snackbar.LENGTH_LONG);
        s.setAction(R.string.undo, v -> {
            filterEngine.createOrUpdateFilter(clone);
            adapter.reload();
        });
        fixSnackbarText(context, s);
        s.show();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            //search off, turn on buttons and touch listener
            adapter.searchQuery = null;
            adapter.filter();
            add.setVisibility(View.VISIBLE);
            enable.setVisibility(View.VISIBLE);
            itemTouchHelper.attachToRecyclerView(recyclerView);
            attached = true;
        } else {
            //search on, turn off buttons and touch listener
            add.setVisibility(View.GONE);
            enable.setVisibility(View.GONE);
            itemTouchHelper.attachToRecyclerView(null);
            attached = false;
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.searchQuery = entered;
        adapter.filter();
    }

    private class FilterAdapter extends RecyclerView.Adapter<FilterCell> {
        private List<Filter> sourceList = new ArrayList<>();
        private List<Filter> displayList = new ArrayList<>();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
            reload();
            filter();
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
            int types = FilterType.forFlags(filter.type).size();
            String subText = context.getResources().getQuantityString(R.plurals.type, types, types);

            subText += " \u2013 ";
            if (filter.allBoards) {
                subText += context.getString(R.string.filter_summary_all_boards);
            } else {
                int size = filterEngine.getFilterBoardCount(filter);
                subText += context.getResources().getQuantityString(R.plurals.board, size, size);
            }

            subText += " \u2013 " + FilterAction.actionName(FilterAction.forId(filter.action));

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

        public void reload() {
            sourceList.clear();
            sourceList.addAll(databaseManager.runTask(databaseManager.getDatabaseFilterManager().getFilters()));
            Collections.sort(sourceList, (o1, o2) -> o1.order - o2.order);
            filter();
        }

        public void move(int from, int to) {
            Filter filter = sourceList.remove(from);
            sourceList.add(to, filter);
            sourceList = setOrders(sourceList);
            databaseManager.runTask(databaseManager.getDatabaseFilterManager().updateFilters(sourceList));
            displayList.clear();
            displayList.addAll(sourceList);
            notifyDataSetChanged();
        }

        private List<Filter> setOrders(List<Filter> input) {
            for (int i = 0; i < input.size(); i++) {
                input.get(i).order = i;
            }
            return input;
        }

        public void filter() {
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
            Collections.sort(displayList, (o1, o2) -> o1.order - o2.order);

            notifyDataSetChanged();
        }
    }

    private class FilterCell extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView text;
        private TextView subtext;
        private ImageView reorder;

        public FilterCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);
            reorder = itemView.findViewById(R.id.reorder);

            Drawable drawable = DrawableCompat.wrap(context.getDrawable(R.drawable.ic_reorder_black_24dp)).mutate();
            DrawableCompat.setTint(drawable, getAttrColor(context, R.attr.text_color_hint));
            reorder.setImageDrawable(drawable);

            reorder.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && attached) {
                    itemTouchHelper.startDrag(FilterCell.this);
                }
                return false;
            });

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position >= 0 && position < adapter.getItemCount() && v == itemView) {
                showFilterDialog(adapter.displayList.get(position));
            }
        }
    }
}
