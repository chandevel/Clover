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
        if (getWatchEnabled() != enabled) {
            ChanApplication.getPreferences().edit().putBoolean("preference_pass_enabled", enabled).commit();
        }
    }
}
