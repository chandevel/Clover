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
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.notification.ThreadWatchNotifications;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.utils.AndroidUtils;

import static org.floens.chan.Chan.injector;

public class WatchSettingsController extends SettingsController implements CompoundButton.OnCheckedChangeListener {
    private CrossfadeView crossfadeView;

    private SettingView enableBackground;

    private SettingView backgroundTimeout;
    private SettingView normalChannel;
    private SettingView mentionChannel;

    public WatchSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        boolean enabled = ChanSettings.watchEnabled.get();

        navigation.setTitle(R.string.settings_screen_watch);

        view = inflateRes(R.layout.controller_watch);
        content = view.findViewById(R.id.scrollview_content);
        crossfadeView = view.findViewById(R.id.crossfade);

        crossfadeView.toggle(enabled, false);

        SwitchCompat globalSwitch = new SwitchCompat(context);
        globalSwitch.setChecked(enabled);
        globalSwitch.setOnCheckedChangeListener(this);
        navigation.setRightView(globalSwitch);

        populatePreferences();

        buildPreferences();

        if (!ChanSettings.watchBackground.get()) {
            setSettingViewVisibility(backgroundTimeout, false, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setSettingViewVisibility(normalChannel, false, false);
                setSettingViewVisibility(mentionChannel, false, false);
            }
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        ChanSettings.watchEnabled.set(isChecked);
        crossfadeView.toggle(isChecked, true);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableBackground) {
            boolean enabled = ChanSettings.watchBackground.get();
            setSettingViewVisibility(backgroundTimeout, enabled, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setSettingViewVisibility(normalChannel, enabled, true);
                setSettingViewVisibility(mentionChannel, enabled, true);
            }
        }
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(R.string.settings_group_watch);

//        settings.add(new BooleanSettingView(this, ChanSettings.watchCountdown, string(R.string.setting_watch_countdown), string(R.string.setting_watch_countdown_description)));
        enableBackground = settings.add(new BooleanSettingView(this, ChanSettings.watchBackground, R.string.setting_watch_enable_background, R.string.setting_watch_enable_background_description));

        int[] timeouts = new int[]{
                60 * 1000,
                5 * 60 * 1000,
                10 * 60 * 1000,
                15 * 60 * 1000,
                30 * 60 * 1000,
                45 * 60 * 1000,
                60 * 60 * 1000,
                2 * 60 * 60 * 1000
        };
        ListSettingView.Item[] timeoutsItems = new ListSettingView.Item[timeouts.length];
        for (int i = 0; i < timeouts.length; i++) {
            int value = timeouts[i] / 1000 / 60;
            String name = context.getResources().getQuantityString(R.plurals.minutes, value, value);
            timeoutsItems[i] = new ListSettingView.Item<>(name, timeouts[i]);
        }
        backgroundTimeout = settings.add(new ListSettingView<Integer>(this, ChanSettings.watchBackgroundInterval, R.string.setting_watch_background_timeout, timeoutsItems) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.setting_watch_background_timeout_description) + "\n\n" + items.get(selected).name;
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            injector().instance(ThreadWatchNotifications.class).ensureChannels();

            normalChannel = settings.add(new LinkSettingView(this, R.string.setting_watch_channel_normal, 0, v -> {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, ThreadWatchNotifications.CHANNEL_ID_WATCH_NORMAL);
                AndroidUtils.openIntent(intent);
            }));

            mentionChannel = settings.add(new LinkSettingView(this, R.string.setting_watch_channel_mention, 0, v -> {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
                intent.putExtra(Settings.EXTRA_CHANNEL_ID, ThreadWatchNotifications.CHANNEL_ID_WATCH_MENTION);
                AndroidUtils.openIntent(intent);
            }));
        }

        groups.add(settings);
    }
}
