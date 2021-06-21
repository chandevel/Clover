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
package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;
import android.content.res.Configuration;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.ui.controller.ToolbarNavigationController;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.SETTINGS_REFRESH_REQUEST;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

public abstract class SettingsController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback {
    private RecyclerView groupRecycler;

    protected List<SettingsGroup> groups = new ArrayList<>();
    protected List<SettingView> requiresUiRefresh = new ArrayList<>();
    // Very user unfriendly.
    protected List<SettingView> requiresRestart = new ArrayList<>();
    private boolean needRestart = false;

    private String filterText = "";
    private final List<SettingsGroup> displayList = new ArrayList<>();

    public SettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = new RecyclerView(context);
        groupRecycler = (RecyclerView) view;
        groupRecycler.setLayoutManager(new LinearLayoutManager(context) {
            @Override
            protected void calculateExtraLayoutSpace(
                    @NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace
            ) {
                // lays out everything at once
                extraLayoutSpace[0] = Integer.MAX_VALUE / 2;
                extraLayoutSpace[1] = Integer.MAX_VALUE / 2;
            }
        });
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor_secondary));

        navigation.buildMenu().withItem(R.drawable.ic_fluent_search_24_filled,
                (item) -> ((ToolbarNavigationController) navigationController).showSearch()
        ).build();

        // populate all the data lists
        populatePreferences();

        // populate all the display lists
        filterSettingGroups(filterText);

        groupRecycler.setAdapter(new SettingsGroupAdapter());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        groupRecycler.setAdapter(null); // unbinds all attached groups

        if (needRestart) {
            ((StartActivity) context).restartApp();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        groupRecycler.getAdapter().notifyDataSetChanged();
    }

    public void onPreferenceChange(SettingView item) {
        for (SettingsGroup group : displayList) {
            if (group.displayList.contains(item)) {
                view.post(() -> groupRecycler.getAdapter().notifyItemChanged(displayList.indexOf(group), item));
            }
        }

        if (requiresUiRefresh.contains(item)) {
            postToEventBus(new RefreshUIMessage(SETTINGS_REFRESH_REQUEST));
        } else if (requiresRestart.contains(item)) {
            needRestart = true;
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        filterSettingGroups("");
    }

    @Override
    public void onSearchEntered(String entered) {
        filterSettingGroups(entered);
    }

    private void filterSettingGroups(String filter) {
        displayList.clear();
        filterText = filter;

        for (SettingsGroup group : groups) {
            group.filter(filter);
            if (!group.displayList.isEmpty() || StringUtils.containsIgnoreCase(group.name, filter)) {
                displayList.add(group);
            }
        }

        if (groupRecycler.getAdapter() != null) {
            groupRecycler.getAdapter().notifyDataSetChanged();
        }
    }

    protected abstract void populatePreferences();

    private void setDescriptionText(View view, String topText, String bottomText) {
        SpannableStringBuilder builder = StringUtils.applySearchSpans(ThemeHelper.getTheme(), topText, filterText);
        ((TextView) view.findViewById(R.id.top)).setText(builder);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            SpannableStringBuilder builder2 =
                    StringUtils.applySearchSpans(ThemeHelper.getTheme(), bottomText, filterText);
            bottom.setText(builder2);
            bottom.setVisibility(TextUtils.isEmpty(bottomText) ? GONE : VISIBLE);
        }
    }

    private class SettingsGroupAdapter
            extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public SettingsGroupAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View inflatedView = LayoutInflater.from(parent.getContext()).inflate(R.layout.setting_group, parent, false);
            RecyclerView settingViewRecycler = inflatedView.findViewById(R.id.setting_view_recycler);
            settingViewRecycler.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));
            return new RecyclerView.ViewHolder(inflatedView) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingsGroup group = displayList.get(position);
            SpannableStringBuilder builder =
                    StringUtils.applySearchSpans(ThemeHelper.getTheme(), group.name, filterText);
            ((TextView) holder.itemView.findViewById(R.id.header)).setText(builder);
            if (position == 0) {
                ((RecyclerView.LayoutParams) holder.itemView.getLayoutParams()).topMargin = 0;
            }

            int margin = 0;
            if (isTablet()) {
                margin = (int) (SettingsController.this.view.getWidth() * 0.1);
            }

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            params.leftMargin = margin;
            params.rightMargin = margin;

            RecyclerView settingViewRecycler = holder.itemView.findViewById(R.id.setting_view_recycler);
            settingViewRecycler.swapAdapter(new SettingViewAdapter(group), false);
        }

        @Override
        public void onBindViewHolder(
                @NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads
        ) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads);
            } else if (payloads.size() == 1 && payloads.get(0) instanceof SettingView) {
                final SettingView settingView = (SettingView) payloads.get(0);
                // called when a preference changes
                SettingsGroup group = displayList.get(position);
                RecyclerView settingViewRecycler = holder.itemView.findViewById(R.id.setting_view_recycler);
                holder.itemView.post(() -> settingViewRecycler.getAdapter()
                        .notifyItemChanged(group.displayList.indexOf(settingView), new Object()));
            }
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            ((RecyclerView) holder.itemView.findViewById(R.id.setting_view_recycler)).setAdapter(null); // unbinds all attached settings
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).name.hashCode();
        }
    }

    private class SettingViewAdapter
            extends RecyclerView.Adapter<SettingViewAdapter.SettingViewHolder> {
        private final SettingsGroup group;

        public SettingViewAdapter(SettingsGroup group) {
            this.group = group;
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public SettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View preferenceView;
            if (viewType == BooleanSettingView.class.getSimpleName().hashCode()) {
                preferenceView =
                        LayoutInflater.from(parent.getContext()).inflate(R.layout.setting_boolean, parent, false);
            } else {
                preferenceView = LayoutInflater.from(parent.getContext()).inflate(R.layout.setting_link, parent, false);
            }
            return new SettingViewHolder(preferenceView);
        }

        @Override
        public void onBindViewHolder(@NonNull SettingViewHolder holder, int position) {
            SettingView settingView = group.displayList.get(position);
            holder.settingView = settingView;

            int itemMargin = 0;
            if (isTablet()) {
                itemMargin = dp(16);
            }

            updatePaddings(holder.itemView, itemMargin, itemMargin, -1, -1);

            setDescriptionText(holder.itemView, settingView.getTopDescription(), settingView.getBottomDescription());
            settingView.setView(holder.itemView);
            try {
                if (settingView instanceof LinkSettingView) {
                    EventBus.getDefault().register(settingView); // for setting notifications
                }
            } catch (Exception ignored) {}
        }

        @Override
        public void onBindViewHolder(
                @NonNull SettingViewHolder holder, int position, @NonNull List<Object> payloads
        ) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads);
            } else if (payloads.size() == 1) {
                // called when a preference changes
                SettingView settingView = group.displayList.get(position);
                setDescriptionText(
                        holder.itemView,
                        settingView.getTopDescription(),
                        settingView.getBottomDescription()
                );
            }
        }

        @Override
        public void onViewRecycled(
                @NonNull SettingViewHolder holder
        ) {
            try {
                if (holder.settingView instanceof LinkSettingView) {
                    EventBus.getDefault().unregister(holder.settingView); // for setting notifications
                }
            } catch (Exception ignored) {}
            holder.settingView.setView(null);
            holder.settingView = null;
        }

        @Override
        public int getItemCount() {
            return group.displayList.size();
        }

        @Override
        public int getItemViewType(int position) {
            return group.displayList.get(position).getClass().getSimpleName().hashCode();
        }

        @Override
        public long getItemId(int position) {
            return group.displayList.get(position).name.hashCode();
        }

        private class SettingViewHolder
                extends RecyclerView.ViewHolder {
            private SettingView settingView;

            public SettingViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
