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
import android.widget.Switch;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.WatchNotifyMode;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.PrimitiveSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.limitcallbacks.IntegerLimitCallback;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class WatchSettingsController
        extends SettingsController {
    private SettingView enableBackground;

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

        navigation.setTitle(R.string.settings_screen_watch);

        Switch globalSwitch = new Switch(context);
        globalSwitch.setChecked(ChanSettings.watchEnabled.get());
        globalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ChanSettings.watchEnabled.set(isChecked);
            onPreferenceChange(enableBackground);
        });
        navigation.setRightView(globalSwitch);

        switchVisibility(ChanSettings.watchBackground.get());
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableBackground) {
            switchVisibility(ChanSettings.watchBackground.get());
        }
    }

    private void switchVisibility(boolean enabled) {
        enableBackground.setEnabled(ChanSettings.watchEnabled.get());
        backgroundTimeout.setEnabled(enabled && ChanSettings.watchEnabled.get());
        removeWatched.setEnabled(enabled && ChanSettings.watchEnabled.get());
        lastPageNotifyMode.setEnabled(enabled && ChanSettings.watchEnabled.get());
        notifyMode.setEnabled(enabled && ChanSettings.watchEnabled.get());
        soundMode.setEnabled(enabled && ChanSettings.watchEnabled.get());
        peekMode.setEnabled(enabled && ChanSettings.watchEnabled.get());
    }

    @Override
    protected void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(R.string.settings_group_watch);

        enableBackground = settings.add(new BooleanSettingView(this,
                ChanSettings.watchBackground,
                R.string.setting_watch_enable_background,
                R.string.setting_watch_enable_background_description
        ));

        backgroundTimeout = settings.add(new PrimitiveSettingView<Integer>(this,
                ChanSettings.watchBackgroundInterval,
                R.string.setting_watch_background_timeout,
                R.string.setting_watch_background_timeout_dialog,
                null,
                // 1 min to 24 hr
                new IntegerLimitCallback() {
                    @Override
                    public Integer getMinimumLimit() {
                        return 1;
                    }

                    @Override
                    public Integer getMaximumLimit() {
                        return 1440;
                    }
                }
        ) {
            @Override
            public String getBottomDescription() {
                int curSettingMinutes = (int) MILLISECONDS.toMinutes(setting.get());
                if (curSettingMinutes >= 60) {
                    int hours = (int) MINUTES.toHours(curSettingMinutes);
                    int leftOverMinutes = (int) (curSettingMinutes - HOURS.toMinutes(hours));
                    return getQuantityString(R.plurals.hours, hours) + (leftOverMinutes == 0
                            ? ""
                            : " " + getQuantityString(R.plurals.minutes, leftOverMinutes));
                } else {
                    return getQuantityString(R.plurals.minutes, curSettingMinutes);
                }
            }
        });

        removeWatched = settings.add(new BooleanSettingView(this,
                ChanSettings.removeWatchedFromCatalog,
                R.string.setting_remove_watched,
                R.string.empty
        ));

        lastPageNotifyMode = settings.add(new BooleanSettingView(this,
                ChanSettings.watchLastPageNotify,
                R.string.setting_thread_page_limit_notify,
                R.string.setting_thread_page_limit_notify_description
        ));

        notifyMode = settings.add(new ListSettingView<>(this,
                ChanSettings.watchNotifyMode,
                R.string.setting_watch_notify_mode,
                context.getResources().getStringArray(R.array.setting_watch_notify_modes),
                WatchNotifyMode.values()
        ));

        soundMode = settings.add(new ListSettingView<>(this,
                ChanSettings.watchSound,
                R.string.setting_watch_sound,
                context.getResources().getStringArray(R.array.setting_watch_notify_modes),
                WatchNotifyMode.values()
        ));

        peekMode = settings.add(new BooleanSettingView(this,
                ChanSettings.watchPeek,
                R.string.setting_watch_peek,
                R.string.setting_watch_peek_description
        ));

        groups.add(settings);
    }
}
