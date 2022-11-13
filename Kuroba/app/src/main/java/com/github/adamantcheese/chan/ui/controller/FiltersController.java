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
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
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
import com.github.adamantcheese.chan.core.database.*;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteRegistry;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.FilterLayout;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.utils.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
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
        DEBUG,
        IMPORT
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

    private Snackbar showingSnackbar;

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
        ).withItem(MenuId.IMPORT, R.drawable.ic_fluent_clipboard_code_24_filled, (item -> {
            showToast(context, "Importing 4chanX filters from clipboard…");
            BackgroundUtils.runOnBackgroundThread(() -> import4ChanXFilters(adapter.getItemCount()));
        })).build();

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
        Balloon importHint = AndroidUtils
                .getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("ImportFilter")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.filter_import_hint)
                .build();
        // add, enable, debug, import
        addHint
                .relayShowAlignTop(toggleHint, enable)
                .relayShowAlignBottom(debugHint, navigation.findItem(MenuId.DEBUG).getView())
                .relayShowAlignBottom(importHint, navigation.findItem(MenuId.IMPORT).getView());
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

        if (showingSnackbar != null) {
            showingSnackbar.dismiss();
            showingSnackbar = null;
        }

        showingSnackbar = AndroidUtils.buildCommonSnackbar(view,
                getString(R.string.filter_removed_undo, clone.pattern),
                R.string.undo,
                v -> {
                    filterEngine.createOrUpdateFilter(clone);
                    onFiltersUpdated(null); // always created, clone's ID is always 0
                }
        );
        showingSnackbar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                showingSnackbar = null;
            }
        });
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

    /**
     * From 4chanX's documentation<br>
     * <br><br>
     *
     * <div><code>Filter</code> is disabled.</div>
     * <p>
     * Use <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions" target="_blank">regular expressions</a>, one per line.<br>
     * Lines starting with a <code>#</code> will be ignored.<br>
     * For example, <code>/weeaboo/i</code> will filter posts containing the string `<code>weeaboo</code>`, case-insensitive.<br>
     * MD5 and Unique ID filtering use exact string matching, not regular expressions.
     * </p>
     * <ul>You can use these settings with each regular expression, separate them with semicolons:
     * <li>
     * Per boards, separate them with commas. It is global if not specified. Use <code>sfw</code> and <code>nsfw</code> to reference all worksafe or not-worksafe boards.<br>
     * For example: <code>boards:a,jp;</code>.<br>
     * To specify boards on a particular site, put the beginning of the domain and a slash character before the list.<br>
     * Any initial <code>www.</code> should not be included, and all 4chan domains are considered <code>4chan.org</code>.<br>
     * For example: <code>boards:4:a,jp,sama:a,z;</code>.<br>
     * An asterisk can be used to specify all boards on a site.<br>
     * For example: <code>boards:4:*;</code>.<br>
     * </li>
     * <li>
     * Select boards to be excluded from the filter. The syntax is the same as for the <code>boards:</code> option above.<br>
     * For example: <code>exclude:vg,v;</code>.
     * </li>
     * <li>
     * Filter OPs only along with their threads (`only`) or replies only (`no`).<br>
     * For example: <code>op:only;</code> or <code>op:no;</code>.
     * </li>
     * <li>
     * Filter only posts with files (`only`) or only posts without files (`no`).<br>
     * For example: <code>file:only;</code> or <code>file:no;</code>.
     * </li>
     * <li>
     * Overrule the `Show Stubs` setting if specified: create a stub (`yes`) or not (`no`).<br>
     * For example: <code>stub:yes;</code> or <code>stub:no;</code>.
     * </li>
     * <li>
     * Highlight instead of hiding. You can specify a class name to use with a userstyle.<br>
     * For example: <code>highlight;</code> or <code>highlight:wallpaper;</code>.
     * </li>
     * <li>
     * Highlighted OPs will have their threads put on top of the board index by default.<br>
     * For example: <code>top:yes;</code> or <code>top:no;</code>.
     * </li>
     * <li>
     * Show a desktop notification instead of hiding.<br>
     * For example: <code>notify;</code>.
     * </li>
     * <li>
     * Filters in the "General" section apply to multiple fields, by default <code>subject,name,filename,comment</code>.<br>
     * The fields can be specified with the <code>type</code> option, separated by commas.<br>
     * For example: <code>type:@{filterTypes};</code>.<br>
     * Types can also be combined with a <code>+</code> sign; this indicates the filter applies to the given fields joined by newlines.<br>
     * For example: <code>type:filename+filesize+dimensions;</code>.<br>
     * </li>
     * </ul>
     */
    private void import4ChanXFilters(int order) {
        Filters createdFilters = new Filters();
        String[] chanXfilters = getClipboardContent().toString().split("\n");
        for (String filterString : chanXfilters) {
            String trimmedFilterString = filterString.trim();
            if (trimmedFilterString.isEmpty()) continue;
            if (StringUtils.startsWithAny(trimmedFilterString, "#", "-")) continue;
            String[] filterAttributes = trimmedFilterString.split(";");
            Filter created = new Filter();
            created.type =
                    FilterType.SUBJECT.flag | FilterType.NAME.flag | FilterType.FILENAME.flag | FilterType.COMMENT.flag;
            created.action = FilterAction.REMOVE.ordinal();
            created.order = order;
            for (String filterAttribute : filterAttributes) {
                boolean hasColon = filterAttribute.indexOf(':') != -1;
                String attributeEntry =
                        hasColon ? filterAttribute.substring(0, filterAttribute.indexOf(':')) : filterAttribute;
                String attributeValue = hasColon ? filterAttribute.substring(filterAttribute.indexOf(':') + 1) : "";
                switch (attributeEntry) {
                    case "boards":
                        String[] boardEntries = attributeValue.split(",");
                        StringBuilder boardsList = new StringBuilder();
                        for (int i = 0; i < boardEntries.length; i++) {
                            String entry = boardEntries[i];
                            boolean entryHasColon = entry.indexOf(':') != -1;
                            String entrySite =
                                    entryHasColon ? entry.substring(0, filterAttribute.indexOf(':')) : "4chan";
                            String entryBoard =
                                    entryHasColon ? entry.substring(filterAttribute.indexOf(':') + 1) : entry;

                            // don't care to process this wildcard, user will have to manually adjust
                            if ("*".equals(entryBoard)) continue;

                            int siteModelDatabaseId = 1; // default to what 4chan probably is for the user
                            SiteModel model =
                                    DatabaseUtils.runTask(instance(DatabaseSiteManager.class).getForClassID(SiteRegistry.getClassIdForSiteName(
                                            entrySite)));
                            if (model != null) {
                                siteModelDatabaseId = model.id;
                            }

                            boardsList.append("").append(siteModelDatabaseId);
                            boardsList.append(":");
                            boardsList.append(entryBoard);
                            if (i + 1 != boardEntries.length) {
                                boardsList.append(",");
                            }
                        }
                        created.boards = boardsList.toString();
                        created.allBoards = false;
                        break;
                    case "exclude":
                    case "file":
                    case "im":
                        // unsupported, ignore
                        break;
                    case "op":
                        created.onlyOnOP = "only".equals(attributeValue);
                        break;
                    case "stub":
                        if ("yes".equals(attributeValue)) {
                            created.action = FilterAction.HIDE.ordinal();
                        }
                        break;
                    case "highlight":
                        created.action = FilterAction.COLOR.ordinal();
                        created.color = Color.RED;
                        break;
                    case "top":
                    case "notify":
                        created.action = FilterAction.WATCH.ordinal();
                        break;
                    case "type":
                        String[] types = attributeValue.split("[,+]]");
                        int newType = 0;
                        for (String type : types) {
                            switch (type) {
                                case "name":
                                    newType |= FilterType.NAME.flag;
                                    break;
                                case "uniqueID":
                                    newType |= FilterType.ID.flag;
                                    break;
                                case "tripcode":
                                    newType |= FilterType.TRIPCODE.flag;
                                    break;
                                case "subject":
                                    newType |= FilterType.SUBJECT.flag;
                                    break;
                                case "comment":
                                    newType |= FilterType.COMMENT.flag;
                                    break;
                                case "flag":
                                    newType |= FilterType.FLAG_CODE.flag;
                                    break;
                                case "filename":
                                    newType |= FilterType.FILENAME.flag;
                                    break;
                                case "MD5":
                                    newType |= FilterType.IMAGE_HASH.flag;
                                    break;
                                case "capcode":
                                case "pass":
                                case "email":
                                case "dimensions":
                                case "filesize":
                                default:
                                    // unsupported
                                    break;
                            }
                        }
                        created.type = newType;
                        break;
                    default:
                        // none of these have special processing, but are here so you are aware
                        // no entry, probably regex, MD5 hash, or an ID
                        if (attributeEntry.charAt(0) == '/') {
                            // regex
                            created.pattern = attributeEntry;
                        } else if (attributeEntry.length() == 32) {
                            // probably MD5
                            created.pattern = "/" + attributeEntry + "/";
                        } else {
                            // probably an ID?
                            created.pattern = "/" + attributeEntry + "/";
                        }
                        break;
                }
            }
            createdFilters.add(created);
            order++;
        }
        DatabaseUtils.runTask(databaseFilterManager.createFilters(createdFilters));
        BackgroundUtils.runOnMainThread(() -> {
            showToast(context, "Finished importing filters!");
            postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
            adapter.reload();
        });
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
            String negativeExtra =
                    TextUtils.isEmpty(filter.negativePattern) ? "" : " but not " + filter.negativePattern;
            holder.text.setText(filter.label.isEmpty()
                    ? filter.pattern + negativeExtra
                    : filter.label + " - " + filter.pattern + negativeExtra);
            holder.text.setTextColor(getAttrColor(context,
                    filter.enabled ? android.R.attr.textColorPrimary : android.R.attr.textColorHint
            ));
            holder.subtext.setTextColor(getAttrColor(context,
                    filter.enabled ? android.R.attr.textColorSecondary : android.R.attr.textColorHint
            ));
            holder.enable.setImageResource(filter.enabled
                    ? R.drawable.ic_fluent_star_off_24_filled
                    : R.drawable.ic_fluent_star_24_filled);

            SpannableStringBuilder subText = new SpannableStringBuilder();
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

            String actionName = FilterAction.actionName(FilterAction.values()[filter.action]);
            subText.append(" \u2013 ").append(filter.action == FilterAction.COLOR.ordinal() ? span(actionName,
                    new BackgroundColorSpanHashed(filter.color),
                    new ForegroundColorSpanHashed(getContrastColor(filter.color))
            ) : actionName);

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
