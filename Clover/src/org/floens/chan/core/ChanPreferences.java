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

import org.floens.chan.ChanApplication;
import org.floens.chan.service.WatchService;

public class ChanPreferences {
    public static boolean getOpenLinkConfirmation() {
        return ChanApplication.getPreferences().getBoolean("preference_open_link_confirmation", true);
    }

    public static String getDefaultName() {
        return ChanApplication.getPreferences().getString("preference_default_name", "");
    }

    public static String getDefaultEmail() {
        return ChanApplication.getPreferences().getString("preference_default_email", "");
    }

    public static boolean getDeveloper() {
        return ChanApplication.getPreferences().getBoolean("preference_developer", false);
    }

    public static void setDeveloper(boolean developer) {
        ChanApplication.getPreferences().edit().putBoolean("preference_developer", developer).commit();
    }

    public static String getImageSaveDirectory() {
        return "Chan";
    }

    public static boolean getWatchEnabled() {
        return ChanApplication.getPreferences().getBoolean("preference_watch_enabled", false);
    }

    /**
     * This also calls updateRunningState on the PinnedService to start/stop the
     * service as needed.
     *
     * @param enabled
     */
    public static void setWatchEnabled(boolean enabled) {
        if (getWatchEnabled() != enabled) {
            ChanApplication.getPreferences().edit().putBoolean("preference_watch_enabled", enabled).commit();
            WatchService.updateRunningState(ChanApplication.getInstance());
            ChanApplication.getPinnedManager().onPinsChanged();
        }
    }

    public static boolean getWatchBackgroundEnabled() {
        return ChanApplication.getPreferences().getBoolean("preference_watch_background_enabled", true);
    }

    public static long getWatchBackgroundTimeout() {
        String number = ChanApplication.getPreferences().getString("preference_watch_background_timeout", "0");
        return Integer.parseInt(number) * 1000L;
    }

    public static boolean getVideoAutoPlay() {
        return ChanApplication.getPreferences().getBoolean("preference_autoplay", false);
    }

    public static boolean getPassEnabled() {
        return ChanApplication.getPreferences().getBoolean("preference_pass_enabled", false);
    }

    public static void setPassEnabled(boolean enabled) {
        if (getPassEnabled() != enabled) {
            ChanApplication.getPreferences().edit().putBoolean("preference_pass_enabled", enabled).commit();
        }
    }

    public static String getPassToken() {
        return ChanApplication.getPreferences().getString("preference_pass_token", "");
    }

    public static String getPassPin() {
        return ChanApplication.getPreferences().getString("preference_pass_pin", "");
    }

    public static void setPassId(String id) {
        ChanApplication.getPreferences().edit().putString("preference_pass_id", id).commit();
    }

    public static String getPassId() {
        return ChanApplication.getPreferences().getString("preference_pass_id", "");
    }
}
