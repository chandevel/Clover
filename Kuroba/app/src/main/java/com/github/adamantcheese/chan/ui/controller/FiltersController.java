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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.FilterLayout;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.j256.ormlite.dao.Dao.CreateOrUpdateStatus;
import com.skydoves.balloon.*;

import java.util.Collections;
import java.util.Locale;

import javax.inject.Inject;

public class FiltersController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback {
    enum MenuId {
        SEARCH,
        DEBUG
    }

    @Inject
    DatabaseFilterManager databaseFilterManager;

    @Inject
    FilterEngine filterEngine;

    private RecyclerView recyclerView;
    private int defaultRecyclerBottomMargin;
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
                @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target
        ) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();

            if (from == RecyclerView.NO_POSITION
                    || to == RecyclerView.NO_POSITION
                    || !TextUtils.isEmpty(adapter.searchQuery)) {
                //require that no search is going on while we do the sorting
                return false;
            }

            adapter.move(from, to);
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            if (direction == ItemTouchHelper.LEFT || direction == ItemTouchHelper.RIGHT) {
                int position = viewHolder.getBindingAdapterPosition();
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
        navigation.buildMenu().withItem(MenuId.SEARCH,
                R.drawable.ic_fluent_search_24_filled,
                (item) -> ((ToolbarNavigationController) navigationController).showSearch()
        ).withItem(MenuId.DEBUG,
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
                    postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
                }
        ).build();

        adapter = new FilterAdapter();

        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setAdapter(adapter);
        recyclerView.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));
        defaultRecyclerBottomMargin = ((CoordinatorLayout.LayoutParams) recyclerView.getLayoutParams()).bottomMargin;

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
        attached = true;

        add = view.findViewById(R.id.add);
        add.setOnClickListener(v -> {
            Filter f = new Filter();
            //add to the end of the filter list
            f.order = adapter.getItemCount();
            showFilterDialog(f);
        });

        enable = view.findViewById(R.id.enable);
        setEnableButtonState();
        enable.setOnClickListener(v -> {
            if (!locked) {
                locked = true;
                //if every filter is disabled, enable all of them
                //if every filter is enabled, disable all of them
                //if some filters are enabled, disable those
                Filters enabledFilters = filterEngine.getEnabledFilters();
                Filters allFilters = filterEngine.getAllFilters();
                if (enabledFilters.isEmpty()) {
                    allFilters.setAllEnableState(true);
                    onFiltersUpdated(allFilters);
                } else if (enabledFilters.size() == allFilters.size()) {
                    allFilters.setAllEnableState(false);
                    onFiltersUpdated(allFilters);
                } else {
                    enabledFilters.setAllEnableState(false);
                    onFiltersUpdated(enabledFilters);
                }
            }
        });
    }

    private void onFiltersUpdated(@Nullable Filters filters) {
        if (filters != null) {
            DatabaseUtils.runTask(databaseFilterManager.updateFilters(filters));
        }
        postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
        adapter.reload();
        setEnableButtonState();
    }

    @Override
    public void onNavItemSet() {
        if (navigation.search) return; // bit of a hack to ignore the search change
        Balloon addHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("AddFilter")
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setTextResource(R.string.filter_add_hint)
                .build();
        Balloon toggleHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ToggleFilter")
                .setArrowOrientation(ArrowOrientation.BOTTOM)
                .setTextResource(R.string.filter_toggle_hint)
                .build();
        Balloon debugHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("DebugFilter")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.filter_debug_hint)
                .build();
        // add, enable, debug
        addHint
                .relayShowAlignTop(toggleHint, enable)
                .relayShowAlignBottom(debugHint, navigation.findItem(MenuId.DEBUG).getView());
        addHint.showAlignTop(add);
    }

    public void showFilterDialog(final Filter filter) {
        final View filterLayout = LayoutInflater.from(context).inflate(R.layout.layout_filter, null);
        final FilterLayout layout = filterLayout.findViewById(R.id.filter_layout);

        final AlertDialog alertDialog =
                getDefaultAlertBuilder(context).setView(filterLayout).setPositiveButton("Save", (dialog, which) -> {
                    CreateOrUpdateStatus status = filterEngine.createOrUpdateFilter(layout.getFilter());
                    onFiltersUpdated(status.isUpdated() ? new Filters(layout.getFilter()) : null);
                }).show();

        layout.setCallback(enabled -> alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(enabled));
        layout.setFilter(filter);
    }

    private void deleteFilter(Filter filter) {
        Filter clone = filter.clone();
        filterEngine.deleteFilter(filter);
        onFiltersUpdated(null);

        AndroidUtils.buildCommonSnackbar(view,
                getString(R.string.filter_removed_undo, clone.pattern),
                R.string.undo,
                v -> {
                    filterEngine.createOrUpdateFilter(clone);
                    onFiltersUpdated(null); // always created, clone's ID is always 0
                }
        );
    }

    private void setEnableButtonState() {
        if (filterEngine.getEnabledFilters().isEmpty()) {
            enable.setImageResource(R.drawable.ic_fluent_star_emphasis_24_filled);
        } else {
            enable.setImageResource(R.drawable.ic_fluent_star_prohibited_24_filled);
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            //search off, turn on buttons and touch listener
            adapter.searchQuery = null;
            adapter.filter();
            add.setVisibility(VISIBLE);
            enable.setVisibility(VISIBLE);
            ((CoordinatorLayout.LayoutParams) recyclerView.getLayoutParams()).bottomMargin =
                    defaultRecyclerBottomMargin;
            itemTouchHelper.attachToRecyclerView(recyclerView);
            attached = true;
        } else {
            //search on, turn off buttons and touch listener
            add.setVisibility(GONE);
            enable.setVisibility(GONE);
            ((CoordinatorLayout.LayoutParams) recyclerView.getLayoutParams()).bottomMargin = 0;
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
        private final Filters sourceList = new Filters();
        private final Filters displayList = new Filters();
        private String searchQuery;

        public FilterAdapter() {
            setHasStableIds(true);
            reload();
        }

        @NonNull
        @Override
        public FilterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new FilterHolder(LayoutInflater
                    .from(parent.getContext())
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
            holder.enable.setImageResource(filter.enabled
                    ? R.drawable.ic_fluent_star_off_24_filled
                    : R.drawable.ic_fluent_star_24_filled);

            StringBuilder subText = new StringBuilder();
            int types = FilterType.forFlags(filter.type).size();
            if (types == 1) {
                subText.append(FilterType.forFlags(filter.type).get(0).toString());
            } else {
                subText.append(getQuantityString(R.plurals.type, types));
            }

            subText.append(" \u2013 ");
            if (filter.allBoards) {
                subText.append(getString(R.string.filter_summary_all_boards));
            } else if (filterEngine.getFilterBoardCount(filter) == 1) {
                subText.append(String.format("/%s/", filter.boards.split(":")[1]));
            } else {
                int size = filterEngine.getFilterBoardCount(filter);
                subText.append(getQuantityString(R.plurals.board, size));
            }

            subText.append(" \u2013 ").append(FilterAction.actionName(FilterAction.values()[filter.action]));

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
            sourceList.addAll(filterEngine.getAllFilters());
            filter();
        }

        public void move(int from, int to) {
            Filter filter = sourceList.remove(from);
            sourceList.add(to, filter);
            for (int i = 0; i < sourceList.size(); i++) {
                sourceList.get(i).order = i;
            }
            DatabaseUtils.runTask(databaseFilterManager.updateFilters(sourceList));
            displayList.clear();
            displayList.addAll(sourceList);
            notifyDataSetChanged();
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
            extends RecyclerView.ViewHolder {
        private final TextView text;
        private final TextView subtext;
        private final ImageView enable;

        @SuppressLint("ClickableViewAccessibility")
        public FilterHolder(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);
            enable = itemView.findViewById(R.id.enable);
            ImageView reorder = itemView.findViewById(R.id.reorder);

            enable.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (!locked && position >= 0 && position < adapter.getItemCount()) {
                    Filter f = adapter.displayList.get(position);
                    f.enabled = !f.enabled;
                    onFiltersUpdated(new Filters(f));
                }
            });

            reorder.setOnTouchListener((v, event) -> {
                if (!locked && event.getActionMasked() == MotionEvent.ACTION_DOWN && attached) {
                    itemTouchHelper.startDrag(FilterHolder.this);
                }
                return false;
            });

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (!locked && position >= 0 && position < adapter.getItemCount()) {
                    showFilterDialog(adapter.displayList.get(position));
                }
            });
        }
    }
}
