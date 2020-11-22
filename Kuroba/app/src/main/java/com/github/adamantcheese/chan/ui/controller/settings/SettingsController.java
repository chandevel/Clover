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
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.SETTINGS_REFRESH_REQUEST;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.findViewsById;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

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

        waitForLayout(view, this);
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
        waitForLayout(view, this);
    }

    @Override
    public boolean onMeasured(View view) {
        setMargins();
        return false;
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

    private void setMargins() {
        int margin = 0;
        if (isTablet()) {
            margin = (int) (view.getWidth() * 0.1);
        }

        int itemMargin = 0;
        if (isTablet()) {
            itemMargin = dp(16);
        }

        List<View> groups = findViewsById(content, R.id.group);
        for (View group : groups) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) group.getLayoutParams();
            params.leftMargin = margin;
            params.rightMargin = margin;
            group.setLayoutParams(params);
        }

        List<View> items = findViewsById(content, R.id.preference_item);
        for (View item : items) {
            updatePaddings(item, itemMargin, itemMargin, -1, -1);
        }
    }

    protected void setSettingViewVisibility(SettingView settingView, boolean visible) {
        settingView.view.setVisibility(visible ? VISIBLE : GONE);

        if (settingView.divider != null) {
            settingView.divider.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    protected void setupLayout() {
        view = inflate(context, R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    protected void buildPreferences() {
        boolean firstGroup = true;
        content.removeAllViews();

        for (SettingsGroup group : groups) {
            LinearLayout groupLayout = (LinearLayout) inflate(context, R.layout.setting_group, content, false);
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

                if ((settingView instanceof ListSettingView) || (settingView instanceof LinkSettingView)
                        || (settingView instanceof StringSettingView) || (settingView instanceof IntegerSettingView)) {
                    preferenceView = (ViewGroup) inflate(context, R.layout.setting_link, groupLayout, false);
                } else if (settingView instanceof BooleanSettingView) {
                    preferenceView = (ViewGroup) inflate(context, R.layout.setting_boolean, groupLayout, false);
                }

                if (preferenceView != null) {
                    setDescriptionText(preferenceView, topValue, bottomValue);

                    groupLayout.addView(preferenceView);
                    settingView.setView(preferenceView);
                }

                if (i < group.settingViews.size() - 1) {
                    settingView.divider = inflate(context, R.layout.setting_divider, groupLayout, false);

                    int paddingPx = dp(ChanSettings.fontSize.get() - 6);
                    LinearLayout.LayoutParams dividerParams =
                            (LinearLayout.LayoutParams) settingView.divider.getLayoutParams();
                    dividerParams.leftMargin = paddingPx;
                    dividerParams.rightMargin = paddingPx;
                    settingView.divider.setLayoutParams(dividerParams);

                    groupLayout.addView(settingView.divider);
                }
            }
        }
    }

    protected void updateSettingNotificationIcon(SettingNotification settingNotification, SettingView preferenceView) {
        ImageView notificationIcon = preferenceView.getView().findViewById(R.id.setting_notification_icon);
        if (notificationIcon == null) return; // no notification icon for this view

        notificationIcon.setVisibility(VISIBLE);
        switch (settingNotification) {
            case Default:
                notificationIcon.setVisibility(GONE);
                break;
            case ApkUpdate:
            case CrashLog:
                if (settingNotification == preferenceView.getSettingNotificationType()) {
                    notificationIcon.setImageTintList(ColorStateList.valueOf(getRes().getColor(settingNotification.getNotificationIconTintColor())));
                } else {
                    notificationIcon.setVisibility(GONE);
                }
                break;
            case Both:
                notificationIcon.setImageTintList(ColorStateList.valueOf(getRes().getColor(preferenceView.getSettingNotificationType()
                        .getNotificationIconTintColor())));
                break;
        }
    }

    private void setDescriptionText(View view, String topText, String bottomText) {
        ((TextView) view.findViewById(R.id.top)).setText(topText);

        final TextView bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setText(bottomText);
            bottom.setVisibility(TextUtils.isEmpty(bottomText) ? GONE : VISIBLE);
        }
    }
}
