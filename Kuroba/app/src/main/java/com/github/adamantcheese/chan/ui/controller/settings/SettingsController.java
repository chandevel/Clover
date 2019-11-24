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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.github.adamantcheese.chan.ui.settings.TextSettingView;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class SettingsController
        extends Controller
        implements AndroidUtils.OnMeasuredCallback {
    protected LinearLayout content;
    protected List<SettingsGroup> groups = new ArrayList<>();

    protected List<SettingView> requiresUiRefresh = new ArrayList<>();

    // Very user unfriendly.
    protected List<SettingView> requiresRestart = new ArrayList<>();

    private boolean needRestart = false;

    public SettingsController(Context context) {
        super(context);
    }

    @Override
    public void onShow() {
        super.onShow();

        AndroidUtils.waitForLayout(view, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (needRestart) {
            ((StartActivity) context).restartApp();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidUtils.waitForLayout(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        setMargins();
        return false;
    }

    public void onPreferenceChange(SettingView item) {
        if ((item instanceof ListSettingView) || (item instanceof StringSettingView)
                || (item instanceof IntegerSettingView) || (item instanceof LinkSettingView))
        {
            setDescriptionText(item.view, item.getTopDescription(), item.getBottomDescription());
        }

        if (requiresUiRefresh.contains(item)) {
            EventBus.getDefault().post(new RefreshUIMessage("SettingsController refresh"));
        } else if (requiresRestart.contains(item)) {
            needRestart = true;
        }
    }

    private void setMargins() {
        boolean tablet = AndroidUtils.isTablet(context);

        int margin = 0;
        if (tablet) {
            margin = (int) (view.getWidth() * 0.1);
        }

        int itemMargin = 0;
        if (tablet) {
            itemMargin = dp(16);
        }

        List<View> groups = AndroidUtils.findViewsById(content, R.id.group);
        for (View group : groups) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) group.getLayoutParams();
            params.leftMargin = margin;
            params.rightMargin = margin;
            group.setLayoutParams(params);
        }

        List<View> items = AndroidUtils.findViewsById(content, R.id.preference_item);
        for (View item : items) {
            item.setPadding(itemMargin, item.getPaddingTop(), itemMargin, item.getPaddingBottom());
        }
    }

    protected void setSettingViewVisibility(SettingView settingView, boolean visible) {
        settingView.view.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (settingView.divider != null) {
            settingView.divider.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    protected void setupLayout() {
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    protected void buildPreferences() {
        LayoutInflater inf = LayoutInflater.from(context);
        boolean firstGroup = true;
        content.removeAllViews();

        for (SettingsGroup group : groups) {
            LinearLayout groupLayout = (LinearLayout) inf.inflate(R.layout.setting_group, content, false);
            ((TextView) groupLayout.findViewById(R.id.header)).setText(group.name);

            if (firstGroup) {
                firstGroup = false;
                ((LinearLayout.LayoutParams) groupLayout.getLayoutParams()).topMargin = 0;
            }

            content.addView(groupLayout);

            for (int i = 0; i < group.settingViews.size(); i++) {
                SettingView settingView = group.settingViews.get(i);

                ViewGroup preferenceView = null;
                String topValue = settingView.getTopDescription();
                String bottomValue = settingView.getBottomDescription();
                boolean noDivider = false;

                if ((settingView instanceof ListSettingView) || (settingView instanceof LinkSettingView)
                        || (settingView instanceof StringSettingView) || (settingView instanceof IntegerSettingView))
                {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.setting_link, groupLayout, false);
                } else if (settingView instanceof BooleanSettingView) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.setting_boolean, groupLayout, false);
                } else if (settingView instanceof TextSettingView) {
                    preferenceView = (ViewGroup) inf.inflate(R.layout.setting_text, groupLayout, false);
                    noDivider = true;
                }

                setDescriptionText(preferenceView, topValue, bottomValue);

                groupLayout.addView(preferenceView);

                settingView.setView(preferenceView);

                if (i < group.settingViews.size() - 1 && !noDivider) {
                    settingView.divider = inf.inflate(R.layout.setting_divider, groupLayout, false);
                    groupLayout.addView(settingView.divider);
                }
            }
        }
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setText(bottomText);
            bottom.setVisibility(bottomText == null ? View.GONE : View.VISIBLE);
        }
    }
}
