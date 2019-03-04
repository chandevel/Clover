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
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.view.CrossfadeView;

public class WatchSettingsController extends SettingsController implements CompoundButton.OnCheckedChangeListener {
    private CrossfadeView crossfadeView;

    private SettingView enableBackground;

    private SettingView backgroundTimeout;
    private SettingView lastPageNotifyMode;
    private SettingView notifyMode;
    private SettingView soundMode;
    private SettingView peekMode;
    private SettingView ledMode;

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
            setSettingViewVisibility(lastPageNotifyMode, false, false);
            setSettingViewVisibility(notifyMode, false, false);
            setSettingViewVisibility(soundMode, false, false);
            setSettingViewVisibility(peekMode, false, false);
            setSettingViewVisibility(ledMode, false, false);
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
            setSettingViewVisibility(lastPageNotifyMode, enabled, true);
            setSettingViewVisibility(notifyMode, enabled, true);
            setSettingViewVisibility(soundMode, enabled, true);
            setSettingViewVisibility(peekMode, enabled, true);
            setSettingViewVisibility(ledMode, enabled, true);
        }
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(R.string.settings_group_watch);

//        settings.add(new BooleanSettingView(this, ChanSettings.watchCountdown, string(R.string.setting_watch_countdown), string(R.string.setting_watch_countdown_description)));
        enableBackground = settings.add(new BooleanSettingView(this, ChanSettings.watchBackground, R.string.setting_watch_enable_background, R.string.setting_watch_enable_background_description));

        int[] timeouts = new int[]{
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

        lastPageNotifyMode = settings.add(new BooleanSettingView(this, ChanSettings.watchLastPageNotify, R.string.setting_thread_page_limit_notify, R.string.setting_thread_page_limit_notify_description));

        notifyMode = settings.add(new ListSettingView<>(this, ChanSettings.watchNotifyMode, R.string.setting_watch_notify_mode,
                context.getResources().getStringArray(R.array.setting_watch_notify_modes), new String[]{"all", "quotes"}));

        soundMode = settings.add(new ListSettingView<>(this, ChanSettings.watchSound, R.string.setting_watch_sound,
                context.getResources().getStringArray(R.array.setting_watch_sounds), new String[]{"all", "quotes"}));

        peekMode = settings.add(new BooleanSettingView(this, ChanSettings.watchPeek, R.string.setting_watch_peek, R.string.setting_watch_peek_description));

        ledMode = settings.add(new ListSettingView<>(this, ChanSettings.watchLed, R.string.setting_watch_led,
                context.getResources().getStringArray(R.array.setting_watch_leds),
                new String[]{"-1", "ffffffff", "ffff0000", "ffffff00", "ff00ff00", "ff00ffff", "ff0000ff", "ffff00ff"}));

        groups.add(settings);
    }
}
