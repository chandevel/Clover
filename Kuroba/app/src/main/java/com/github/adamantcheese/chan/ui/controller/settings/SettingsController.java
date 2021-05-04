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
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

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
        extends Controller {

    protected List<SettingsGroup> groups = new ArrayList<>();
    protected List<SettingView> requiresUiRefresh = new ArrayList<>();
    // Very user unfriendly.
    protected List<SettingView> requiresRestart = new ArrayList<>();
    private boolean needRestart = false;

    public SettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = new RecyclerView(context);
        ((RecyclerView) view).setLayoutManager(new LinearLayoutManager(context) {
            @Override
            protected void calculateExtraLayoutSpace(
                    @NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace
            ) {
                extraLayoutSpace[0] = Integer.MAX_VALUE / 2;
                extraLayoutSpace[1] = Integer.MAX_VALUE / 2;
            }
        });
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor_secondary));
        view.setId(R.id.recycler_view);

        populatePreferences();

        ((RecyclerView) view.findViewById(R.id.recycler_view)).setAdapter(new SettingsGroupAdapter());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        ((RecyclerView) view.findViewById(R.id.recycler_view)).setAdapter(null); // unbinds all attached groups

        if (needRestart) {
            ((StartActivity) context).restartApp();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ((RecyclerView) view).getAdapter().notifyDataSetChanged();
    }

    public void onPreferenceChange(SettingView item) {
        if ((item instanceof ListSettingView) || (item instanceof StringSettingView)
                || (item instanceof IntegerSettingView) || (item instanceof LinkSettingView)) {
            setDescriptionText(item.view, item.getTopDescription(), item.getBottomDescription());
        }

        if (requiresUiRefresh.contains(item)) {
            postToEventBus(new RefreshUIMessage(SETTINGS_REFRESH_REQUEST));
        } else if (requiresRestart.contains(item)) {
            needRestart = true;
        }
    }

    protected abstract void populatePreferences();

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setText(bottomText);
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
            SettingsGroup group = groups.get(position);
            ((TextView) holder.itemView.findViewById(R.id.header)).setText(group.name);
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
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            ((RecyclerView) holder.itemView.findViewById(R.id.setting_view_recycler)).setAdapter(null); // unbinds all attached settings
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        @Override
        public int getItemViewType(int position) {
            return groups.get(position).name.hashCode();
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
            int itemMargin = 0;
            if (isTablet()) {
                itemMargin = dp(16);
            }

            updatePaddings(holder.itemView, itemMargin, itemMargin, -1, -1);

            SettingView settingView = group.settingViews.get(position);
            setDescriptionText(holder.itemView, settingView.getTopDescription(), settingView.getBottomDescription());
            settingView.setView(holder.itemView);
            try {
                if (settingView instanceof LinkSettingView) {
                    EventBus.getDefault().register(settingView); // for setting notifications
                }
            } catch (Exception ignored) {}
        }

        @Override
        public void onViewRecycled(
                @NonNull SettingViewHolder holder
        ) {
            SettingView settingView = group.settingViews.get(holder.getAdapterPosition());
            try {
                if (settingView instanceof LinkSettingView) {
                    EventBus.getDefault().unregister(settingView); // for setting notifications
                }
            } catch (Exception ignored) {}
        }

        @Override
        public int getItemCount() {
            return group.settingViews.size();
        }

        @Override
        public int getItemViewType(int position) {
            return group.settingViews.get(position).getClass().getSimpleName().hashCode();
        }

        private class SettingViewHolder
                extends RecyclerView.ViewHolder {
            public SettingViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}
