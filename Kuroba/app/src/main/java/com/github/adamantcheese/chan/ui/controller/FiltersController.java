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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.FilterLayout;
import com.github.adamantcheese.chan.ui.widget.CancellableSnackbar;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;
import com.skydoves.balloon.Balloon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.ui.controller.FiltersController.MenuId.DEBUG;
import static com.github.adamantcheese.chan.ui.controller.FiltersController.MenuId.SEARCH;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

public class FiltersController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback, View.OnClickListener {
    enum MenuId {
        SEARCH,
        DEBUG
    }

    @Inject
    DatabaseFilterManager databaseFilterManager;

    @Inject
    FilterEngine filterEngine;

    private RecyclerView recyclerView;
    private FloatingActionButton add;
    private FloatingActionButton enable;
    private FilterAdapter adapter;
    private boolean locked;

    private ItemTouchHelper itemTouchHelper;
    private boolean attached;

    private final ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
    ) {
        @Override
        public boolean onMove(
                RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target
        ) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                    || !TextUtils.isEmpty(adapter.searchQuery)) {
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

        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_filters, null);

        navigation.setTitle(R.string.filters_screen);
        navigation.swipeable = false;
        navigation.buildMenu().withItem(SEARCH,
                R.drawable.ic_fluent_search_24_filled,
                (item) -> ((ToolbarNavigationController) navigationController).showSearch()
        ).withItem(DEBUG,
                ChanSettings.debugFilters.get()
                        ? R.drawable.ic_fluent_highlight_24_filled
                        : R.drawable.ic_fluent_highlight_24_regular,
                (item) -> {
                    ChanSettings.debugFilters.toggle();
                    item.setImage(ChanSettings.debugFilters.get()
                            ? R.drawable.ic_fluent_highlight_24_filled
                            : R.drawable.ic_fluent_highlight_24_regular);
                    showToast(context,
                            "Filter debugging turned " + (ChanSettings.debugFilters.get()
                                    ? "on; tap highlighted text to see matched filter."
                                    : "off.")
                    );
                }
        ).build();

        adapter = new FilterAdapter();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attached = true;

        add = view.findViewById(R.id.add);
        add.setOnClickListener(this);

        enable = view.findViewById(R.id.enable);
        enable.setOnClickListener(this);
    }

    @Override
    public void onNavItemSet() {
        if (navigation.search) return; // bit of a hack to ignore the search change
        Balloon addHint = AndroidUtils.getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("AddFilter")
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setTextResource(R.string.filter_add_hint)
                .build();
        Balloon toggleHint = AndroidUtils.getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ToggleFilter")
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setTextResource(R.string.filter_toggle_hint)
                .build();
        Balloon debugHint = AndroidUtils.getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("DebugFilter")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.filter_debug_hint)
                .build();
        addHint.relayShowAlignTop(toggleHint, enable)
                .relayShowAlignBottom(debugHint, navigation.findItem(DEBUG).getView());
        addHint.showAlignTop(add);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        databaseFilterManager.updateFilters(adapter.sourceList);
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            Filter f = new Filter();
            //add to the end of the filter list
            f.order = adapter.getItemCount();
            showFilterDialog(f);
        } else if (v == enable && !locked) {
            FloatingActionButton enableButton = (FloatingActionButton) v;
            locked = true;
            //if every filter is disabled, enable all of them and set the drawable to be an x
            //if every filter is enabled, disable all of them and set the drawable to be a checkmark
            //if some filters are enabled, disable them and set the drawable to be a checkmark
            List<Filter> enabledFilters = filterEngine.getEnabledFilters();
            List<Filter> allFilters = filterEngine.getAllFilters();
            if (enabledFilters.isEmpty()) {
                setFilters(allFilters, true);
                enableButton.setImageResource(R.drawable.ic_fluent_dismiss_24_filled);
            } else if (enabledFilters.size() == allFilters.size()) {
                setFilters(allFilters, false);
                enableButton.setImageResource(R.drawable.ic_fluent_checkmark_24_filled);
            } else {
                setFilters(enabledFilters, false);
                enableButton.setImageResource(R.drawable.ic_fluent_checkmark_24_filled);
            }
            postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
        }
    }

    private void setFilters(List<Filter> filters, boolean enabled) {
        for (Filter filter : filters) {
            filter.enabled = enabled;
            filterEngine.createOrUpdateFilter(filter);
        }
        adapter.reload();
    }

    public void showFilterDialog(final Filter filter) {
        final View filterLayout = LayoutInflater.from(context).inflate(R.layout.layout_filter, null);
        final FilterLayout layout = filterLayout.findViewById(R.id.filter_layout);

        final AlertDialog alertDialog =
                getDefaultAlertBuilder(context).setView(filterLayout).setPositiveButton("Save", (dialog, which) -> {
                    filterEngine.createOrUpdateFilter(layout.getFilter());
                    if (filterEngine.getEnabledFilters().isEmpty()) {
                        enable.setImageResource(R.drawable.ic_fluent_checkmark_24_filled);
                    } else {
                        enable.setImageResource(R.drawable.ic_fluent_dismiss_24_filled);
                    }
                    postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
                    adapter.reload();
                }).show();

        layout.setCallback(enabled -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled));
        layout.setFilter(filter);
    }

    private void deleteFilter(Filter filter) {
        Filter clone = filter.clone();
        filterEngine.deleteFilter(filter);
        postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
        adapter.reload();

        CancellableSnackbar.showSnackbar(view,
                getString(R.string.filter_removed_undo, clone.pattern),
                R.string.undo,
                v -> {
                    filterEngine.createOrUpdateFilter(clone);
                    adapter.reload();
                }
        );
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            //search off, turn on buttons and touch listener
            adapter.searchQuery = null;
            adapter.filter();
            add.setVisibility(VISIBLE);
            enable.setVisibility(VISIBLE);
            itemTouchHelper.attachToRecyclerView(recyclerView);
            attached = true;
        } else {
            //search on, turn off buttons and touch listener
            add.setVisibility(GONE);
            enable.setVisibility(GONE);
            itemTouchHelper.attachToRecyclerView(null);
            attached = false;
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.searchQuery = entered;
        adapter.filter();
    }

    private class FilterAdapter
            extends RecyclerView.Adapter<FilterHolder> {
        private List<Filter> sourceList = new ArrayList<>();
        private final List<Filter> displayList = new ArrayList<>();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
            reload();
            filter();
        }

        @Override
        public FilterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new FilterHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cell_filter, parent, false));
        }

        @Override
        public void onBindViewHolder(FilterHolder holder, int position) {
            Filter filter = displayList.get(position);
            holder.text.setText(filter.pattern);
            holder.text.setTextColor(getAttrColor(context,
                    filter.enabled ? android.R.attr.textColorPrimary : android.R.attr.textColorHint
            ));
            holder.subtext.setTextColor(getAttrColor(context,
                    filter.enabled ? android.R.attr.textColorSecondary : android.R.attr.textColorHint
            ));

            StringBuilder subText = new StringBuilder();
            int types = FilterType.forFlags(filter.type).size();
            if (types == 1) {
                subText.append(FilterType.filterTypeName(FilterType.forFlags(filter.type).get(0)));
            } else {
                subText.append(String.format(Locale.ENGLISH, "%d types", types));
            }

            subText.append(" \u2013 ");
            if (filter.allBoards) {
                subText.append(getString(R.string.filter_summary_all_boards));
            } else if (filterEngine.getFilterBoardCount(filter) == 1) {
                subText.append(String.format("/%s/", filter.boards.split(":")[1]));
            } else {
                int size = filterEngine.getFilterBoardCount(filter);
                subText.append(String.format(Locale.ENGLISH, "%d boards", size));
            }

            subText.append(" \u2013 ").append(FilterAction.actionName(FilterAction.forId(filter.action)));

            holder.subtext.setText(subText.toString());
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
            sourceList.addAll(DatabaseUtils.runTask(databaseFilterManager.getFilters()));
            Collections.sort(sourceList, (o1, o2) -> o1.order - o2.order);
            filter();
        }

        public void move(int from, int to) {
            Filter filter = sourceList.remove(from);
            sourceList.add(to, filter);
            sourceList = setOrders(sourceList);
            DatabaseUtils.runTask(databaseFilterManager.updateFilters(sourceList));
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
            locked = false;
        }
    }

    private class FilterHolder
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        private final TextView text;
        private final TextView subtext;

        @SuppressLint("ClickableViewAccessibility")
        public FilterHolder(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);
            ImageView reorder = itemView.findViewById(R.id.reorder);

            reorder.setOnTouchListener((v, event) -> {
                if (!locked && event.getActionMasked() == MotionEvent.ACTION_DOWN && attached) {
                    itemTouchHelper.startDrag(FilterHolder.this);
                }
                return false;
            });

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (!locked && position >= 0 && position < adapter.getItemCount() && v == itemView) {
                showFilterDialog(adapter.displayList.get(position));
            }
        }
    }
}
