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
package org.floens.chan.core;

import android.content.SharedPreferences;
import android.os.Environment;

import org.floens.chan.ChanApplication;

import java.io.File;

public class ChanPreferences {
    private static SharedPreferences p() {
        return ChanApplication.getPreferences();
    }

    public static boolean getOpenLinkConfirmation() {
        return p().getBoolean("preference_open_link_confirmation", true);
    }

    public static String getDefaultName() {
        return p().getString("preference_default_name", "");
    }

    public static boolean getPinOnPost() {
        return p().getBoolean("preference_pin_on_post", false);
    }

    public static boolean getDeveloper() {
        return p().getBoolean("preference_developer", false);
    }

    public static void setDeveloper(boolean developer) {
        p().edit().putBoolean("preference_developer", developer).commit();
    }

    public static File getImageSaveDirectory() {
        String path = p().getString("preference_image_save_location", null);
        File file;
        if (path == null) {
            file = new File(Environment.getExternalStorageDirectory() + File.separator + "Clover");
        } else {
            file = new File(path);
        }

        return file;
    }

    public static void setImageSaveDirectory(File file) {
        p().edit().putString("preference_image_save_location", file.getAbsolutePath()).commit();
    }

    public static boolean getImageSaveOriginalFilename() {
        return p().getBoolean("preference_image_save_original", false);
    }

    public static boolean getImageShareUrl() {
        return p().getBoolean("preference_image_share_url", false);
    }

    public static boolean getWatchEnabled() {
        return p().getBoolean("preference_watch_enabled", false);
    }

    /**
     * This also calls updateRunningState on the PinnedService to start/stop the
     * service as needed.
     *
     * @param enabled
     */
    public static void setWatchEnabled(boolean enabled) {
        if (getWatchEnabled() != enabled) {
            p().edit().putBoolean("preference_watch_enabled", enabled).commit();
            ChanApplication.getWatchManager().onWatchEnabledChanged(enabled);
        }
    }

    public static boolean getWatchCountdownVisibleEnabled() {
        return p().getBoolean("preference_watch_countdown", false);
    }

    public static boolean getWatchBackgroundEnabled() {
        return p().getBoolean("preference_watch_background_enabled", false);
    }

    public static int getWatchBackgroundTimeout() {
        String number = p().getString("preference_watch_background_timeout", "60");
        return Integer.parseInt(number);
    }

    public static String getWatchNotifyMode() {
        return p().getString("preference_watch_notify_mode", "all");
    }

    public static String getWatchSound() {
        return p().getString("preference_watch_sound", "quotes");
    }

    public static long getWatchLed() {
        String raw = p().getString("preference_watch_led", "ffffffff");
        return Long.parseLong(raw, 16);
    }

    public static boolean getVideoAutoPlay() {
        return getImageAutoLoad() && !getVideoExternal() && p().getBoolean("preference_autoplay", false);
    }

    public static boolean getThreadAutoRefresh() {
        return p().getBoolean("preference_auto_refresh_thread", true);
    }

    public static boolean getImageAutoLoad() {
        return p().getBoolean("preference_image_auto_load", true);
    }

    public static boolean getPassEnabled() {
        return p().getBoolean("preference_pass_enabled", false);
    }

    public static void setPassEnabled(boolean enabled) {
        if (getPassEnabled() != enabled) {
            p().edit().putBoolean("preference_pass_enabled", enabled).commit();
        }
    }

    public static String getPassToken() {
        return p().getString("preference_pass_token", "");
    }

    public static String getPassPin() {
        return p().getString("preference_pass_pin", "");
    }

    public static void setPassId(String id) {
        p().edit().putString("preference_pass_id", id).commit();
    }

    public static String getPassId() {
        return p().getString("preference_pass_id", "");
    }

    public static String getTheme() {
        return p().getString("preference_theme", "light");
    }

    public static boolean getForcePhoneLayout() {
        return p().getBoolean("preference_force_phone_layout", false);
    }

    public static boolean getBoardEditorFillerEnabled() {
        return p().getBoolean("preference_board_editor_filler", false);
    }

    public static boolean setBoardEditorFillerEnabled(boolean enabled) {
        return p().edit().putBoolean("preference_board_editor_filler", enabled).commit();
    }

    public static String getBoardViewMode() {
        return p().getString("preference_board_view_mode", "list");
    }

    public static void setBoardViewMode(String mode) {
        p().edit().putString("preference_board_view_mode", mode).commit();
    }

    public static boolean getAnonymize() {
        return p().getBoolean("preference_anonymize", false);
    }

    public static boolean getAnonymizeIds() {
        return p().getBoolean("preference_anonymize_ids", false);
    }

    public static boolean getReplyButtonsBottom() {
        return p().getBoolean("preference_buttons_bottom", false);
    }

    public static String getBoardMode() {
        return p().getString("preference_board_mode", "catalog");
    }

    public static boolean getVideoErrorIgnore() {
        return p().getBoolean("preference_video_error_ignore", false);
    }

    public static void setVideoErrorIgnore(boolean show) {
        p().edit().putBoolean("preference_video_error_ignore", show).commit();
    }

    public static boolean getVideoExternal() {
        return p().getBoolean("preference_video_external", false);
    }

    public static int getFontSize() {
        String font = p().getString("preference_font", null);
        return font == null ? 14 : Integer.parseInt(font);
    }

    public static boolean getNetworkHttps() {
        return p().getBoolean("preference_network_https", true);
    }
}
