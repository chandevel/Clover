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
package org.floens.chan.core.settings;

import android.content.SharedPreferences;
import android.os.Environment;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;

public class ChanSettings {
    public static final StringSetting theme;
    public static final StringSetting fontSize;
    public static final BooleanSetting openLinkConfirmation;
    public static final BooleanSetting autoRefreshThread;
    public static final BooleanSetting imageAutoLoad;
    public static final BooleanSetting videoAutoLoad;
    public static final BooleanSetting videoOpenExternal;
    public static final BooleanSetting videoErrorIgnore;

    public static final StringSetting postDefaultName;
    public static final BooleanSetting postPinThread;

    public static final BooleanSetting developer;

    public static final StringSetting saveLocation;
    public static final BooleanSetting saveOriginalFilename;
    public static final BooleanSetting shareUrl;
    public static final BooleanSetting networkHttps;
    public static final BooleanSetting forcePhoneLayout;
    public static final BooleanSetting anonymize;
    public static final BooleanSetting anonymizeIds;
    public static final BooleanSetting repliesButtonsBottom;
    public static final BooleanSetting confirmExit;

    public static final BooleanSetting watchEnabled;
    public static final BooleanSetting watchCountdown;
    public static final BooleanSetting watchBackground;
    public static final StringSetting watchBackgroundTimeout;
    public static final StringSetting watchNotifyMode;
    public static final StringSetting watchSound;
    public static final StringSetting watchLed;

    public static final StringSetting passToken;
    public static final StringSetting passPin;
    public static final StringSetting passId;

    static {
        SharedPreferences p = AndroidUtils.getPreferences();

        theme = new StringSetting(p, "preference_theme", "light");
        fontSize = new StringSetting(p, "preference_font", "14");
        openLinkConfirmation = new BooleanSetting(p, "preference_open_link_confirmation", true);
        autoRefreshThread = new BooleanSetting(p, "preference_auto_refresh_thread", true);
        imageAutoLoad = new BooleanSetting(p, "preference_image_auto_load", true);
        videoAutoLoad = new BooleanSetting(p, "preference_autoplay", false);
        videoOpenExternal = new BooleanSetting(p, "preference_video_external", false);
        videoErrorIgnore = new BooleanSetting(p, "preference_video_error_ignore", false);

        postDefaultName = new StringSetting(p, "preference_default_name", "");
        postPinThread = new BooleanSetting(p, "preference_pin_on_post", false);

        developer = new BooleanSetting(p, "preference_developer", false);

        saveLocation = new StringSetting(p, "preference_image_save_location", Environment.getExternalStorageDirectory() + File.separator + "Clover");
        saveOriginalFilename = new BooleanSetting(p, "preference_image_save_original", false);
        shareUrl = new BooleanSetting(p, "preference_image_share_url", false);
        networkHttps = new BooleanSetting(p, "preference_network_https", true, new Setting.SettingCallback<Boolean>() {
            @Override
            public void onValueChange(Setting setting, Boolean value) {
                ChanUrls.loadScheme(value);
            }
        });
        forcePhoneLayout = new BooleanSetting(p, "preference_force_phone_layout", false);
        anonymize = new BooleanSetting(p, "preference_anonymize", false);
        anonymizeIds = new BooleanSetting(p, "preference_anonymize_ids", false);
        repliesButtonsBottom = new BooleanSetting(p, "preference_buttons_bottom", false);
        confirmExit = new BooleanSetting(p, "preference_confirm_exit", false);

        watchEnabled = new BooleanSetting(p, "preference_watch_enabled", false, new Setting.SettingCallback<Boolean>() {
            @Override
            public void onValueChange(Setting setting, Boolean value) {
                Chan.getWatchManager().onWatchEnabledChanged(value);
            }
        });
        watchCountdown = new BooleanSetting(p, "preference_watch_countdown", false);
        watchBackground = new BooleanSetting(p, "preference_watch_background_enabled", false, new Setting.SettingCallback<Boolean>() {
            @Override
            public void onValueChange(Setting setting, Boolean value) {
                Chan.getWatchManager().onBackgroundWatchingChanged(value);
            }
        });
        watchBackgroundTimeout = new StringSetting(p, "preference_watch_background_timeout", "60");
        watchNotifyMode = new StringSetting(p, "preference_watch_notify_mode", "all");
        watchSound = new StringSetting(p, "preference_watch_sound", "quotes");
        watchLed = new StringSetting(p, "preference_watch_led", "ffffffff");

        passToken = new StringSetting(p, "preference_pass_token", "");
        passPin = new StringSetting(p, "preference_pass_pin", "");
        passId = new StringSetting(p, "preference_pass_id", "");

        // Old (but possibly still in some users phone)
        // preference_board_view_mode default "list"
        // preference_board_editor_filler default false
        // preference_pass_enabled default false
    }

    public static boolean passLoggedIn() {
        return passId.get().length() > 0;
    }
}
