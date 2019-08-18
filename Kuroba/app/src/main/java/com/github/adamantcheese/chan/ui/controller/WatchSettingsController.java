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

import android.content.Context;
import android.widget.CompoundButton;

import androidx.appcompat.widget.SwitchCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;

public class WatchSettingsController extends SettingsController implements CompoundButton.OnCheckedChangeListener {
    private CrossfadeView crossfadeView;

    private SettingView enableBackground;
    private SettingView enableFilterWatch;

    private SettingView backgroundTimeout;
    private SettingView removeWatched;
    private SettingView lastPageNotifyMode;
    private SettingView notifyMode;
    private SettingView soundMode;
    private SettingView peekMode;

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
            switchVisibility(false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        ChanSettings.watchEnabled.set(isChecked);

        if (!isChecked) {
            ChanSettings.watchBackground.setSync(false);
            groups.clear();

            populatePreferences();
            buildPreferences();
            switchVisibility(false);
        }

        crossfadeView.toggle(isChecked, true);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableBackground) {
            boolean enabled = ChanSettings.watchBackground.get();
            switchVisibility(enabled);
        }
    }

    private void switchVisibility(boolean enabled) {
        setSettingViewVisibility(backgroundTimeout, enabled);
        setSettingViewVisibility(removeWatched, enabled);
        setSettingViewVisibility(lastPageNotifyMode, enabled);
        setSettingViewVisibility(notifyMode, enabled);
        setSettingViewVisibility(soundMode, enabled);
        setSettingViewVisibility(peekMode, enabled);
        setSettingViewVisibility(enableFilterWatch, enabled);
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(R.string.settings_group_watch);

        settings.add(new BooleanSettingView(this, ChanSettings.shortPinInfo,
                context.getString(R.string.setting_bookmark_short_info), context.getString(R.string.setting_bookmark_short_info_description)));

//        settings.add(new BooleanSettingView(this, ChanSettings.watchCountdown, string(R.string.setting_watch_countdown), string(R.string.setting_watch_countdown_description)));
        enableBackground = settings.add(new BooleanSettingView(this, ChanSettings.watchBackground, R.string.setting_watch_enable_background, R.string.setting_watch_enable_background_description));

        int[] timeouts = new int[]{
                2 * 60 * 1000,
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
            String name = content.getResources().getString(R.string.minutes, timeouts[i] / 1000 / 60);
            timeoutsItems[i] = new ListSettingView.Item<>(name, timeouts[i]);
        }
        backgroundTimeout = settings.add(new ListSettingView<Integer>(this, ChanSettings.watchBackgroundInterval, R.string.setting_watch_background_timeout, timeoutsItems) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.setting_watch_background_timeout_description) + "\n\n" + items.get(selected).name;
            }
        });

        removeWatched = settings.add(new BooleanSettingView(this, ChanSettings.removeWatchedFromCatalog, R.string.setting_remove_watched, R.string.empty));

        lastPageNotifyMode = settings.add(new BooleanSettingView(this, ChanSettings.watchLastPageNotify, R.string.setting_thread_page_limit_notify, R.string.setting_thread_page_limit_notify_description));

        notifyMode = settings.add(new ListSettingView<>(this, ChanSettings.watchNotifyMode, R.string.setting_watch_notify_mode,
                context.getResources().getStringArray(R.array.setting_watch_notify_modes), new String[]{"all", "quotes"}));

        soundMode = settings.add(new ListSettingView<>(this, ChanSettings.watchSound, R.string.setting_watch_sound,
                context.getResources().getStringArray(R.array.setting_watch_sounds), new String[]{"all", "quotes"}));

        peekMode = settings.add(new BooleanSettingView(this, ChanSettings.watchPeek, R.string.setting_watch_peek, R.string.setting_watch_peek_description));

        enableFilterWatch = settings.add(new BooleanSettingView(this, ChanSettings.watchFilterWatch, R.string.setting_watch_enable_filter_watch, R.string.setting_watch_enable_filter_watch_description));

        groups.add(settings);
    }
}
