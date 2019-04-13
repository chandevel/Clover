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

import android.os.Environment;
import android.text.TextUtils;

import org.floens.chan.BuildConfig;
import org.floens.chan.R;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.update.UpdateManager;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

public class ChanSettings {
    private static final String SETTINGS_SEPARATOR = "@@";

    public enum MediaAutoLoadMode implements OptionSettingItem {
        // ALways auto load, either wifi or mobile
        ALL("all"),
        // Only auto load if on wifi
        WIFI("wifi"),
        // Never auto load
        NONE("none");

        String name;

        MediaAutoLoadMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum PostViewMode implements OptionSettingItem {
        LIST("list"),
        CARD("grid");

        String name;

        PostViewMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum LayoutMode implements OptionSettingItem {
        AUTO("auto"),
        PHONE("phone"),
        SLIDE("slide"),
        SPLIT("split");

        String name;

        LayoutMode(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    private static Proxy proxy;

    public static final BooleanSetting forceEnglishLocale;
    private static final StringSetting theme;
    public static final OptionsSetting<LayoutMode> layoutMode;
    public static final StringSetting fontSize;
    public static final BooleanSetting fontCondensed;
    public static final BooleanSetting openLinkConfirmation;
    public static final BooleanSetting openLinkBrowser;
    public static final BooleanSetting autoRefreshThread;
    //    public static final BooleanSetting imageAutoLoad;
    public static final OptionsSetting<MediaAutoLoadMode> imageAutoLoadNetwork;
    public static final OptionsSetting<MediaAutoLoadMode> videoAutoLoadNetwork;
    public static final BooleanSetting videoOpenExternal;
    public static final BooleanSetting videoUseExoplayer;
    public static final BooleanSetting textOnly;
    public static final BooleanSetting videoErrorIgnore;
    public static final OptionsSetting<PostViewMode> boardViewMode;
    public static final IntegerSetting boardGridSpanCount;
    public static final StringSetting boardOrder;

    public static final StringSetting postDefaultName;
    public static final BooleanSetting postPinThread;

    public static final BooleanSetting developer;

    public static final StringSetting saveLocation;
    public static final BooleanSetting saveOriginalFilename;
    public static final BooleanSetting shareUrl;
    public static final BooleanSetting enableReplyFab;
    public static final BooleanSetting anonymize;
    public static final BooleanSetting anonymizeIds;
    public static final BooleanSetting showAnonymousName;
    public static final BooleanSetting revealImageSpoilers;
    public static final BooleanSetting revealTextSpoilers;
    public static final BooleanSetting repliesButtonsBottom;
    public static final BooleanSetting confirmExit;
    public static final BooleanSetting tapNoReply;
    public static final BooleanSetting volumeKeysScrolling;
    public static final BooleanSetting postFullDate;
    public static final BooleanSetting postFileInfo;
    public static final BooleanSetting postFilename;
    public static final BooleanSetting neverHideToolbar;
    public static final BooleanSetting controllerSwipeable;
    public static final BooleanSetting saveBoardFolder;
    public static final BooleanSetting videoDefaultMuted;
    public static final BooleanSetting videoAutoLoop;

    public static final BooleanSetting watchEnabled;
    public static final BooleanSetting watchCountdown;
    public static final BooleanSetting watchBackground;
    public static final IntegerSetting watchBackgroundInterval;
    public static final StringSetting watchNotifyMode;
    public static final StringSetting watchSound;
    public static final BooleanSetting watchPeek;
    public static final StringSetting watchLed;

    public static final BooleanSetting historyEnabled;

    public static final IntegerSetting previousVersion;

    public static final BooleanSetting proxyEnabled;
    public static final StringSetting proxyAddress;
    public static final IntegerSetting proxyPort;

    public static final CounterSetting historyOpenCounter;
    public static final CounterSetting threadOpenCounter;

    public static final LongSetting updateCheckTime;
    public static final LongSetting updateCheckInterval;

    public static final BooleanSetting crashReporting;

    private static final List<Setting<?>> allSettings = new ArrayList<>();

    static {
        SettingProvider p = new SharedPreferencesSettingProvider(AndroidUtils.getPreferences());

        forceEnglishLocale = new BooleanSetting(p, "preference_force_english_locale", false);

        theme = new StringSetting(p, "preference_theme", "yotsuba");

        layoutMode = new OptionsSetting<>(p, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);

        boolean tablet = AndroidUtils.getRes().getBoolean(R.bool.is_tablet);

        fontSize = new StringSetting(p, "preference_font", tablet ? "16" : "14");
        fontCondensed = new BooleanSetting(p, "preference_font_condensed", false);
        openLinkConfirmation = new BooleanSetting(p, "preference_open_link_confirmation", false);
        openLinkBrowser = new BooleanSetting(p, "preference_open_link_browser", false);
        autoRefreshThread = new BooleanSetting(p, "preference_auto_refresh_thread", true);
//        imageAutoLoad = new BooleanSetting(p, "preference_image_auto_load", true);
        imageAutoLoadNetwork = new OptionsSetting<>(p, "preference_image_auto_load_network", MediaAutoLoadMode.class, MediaAutoLoadMode.WIFI);
        videoAutoLoadNetwork = new OptionsSetting<>(p, "preference_video_auto_load_network", MediaAutoLoadMode.class, MediaAutoLoadMode.WIFI);
        videoOpenExternal = new BooleanSetting(p, "preference_video_external", false);
        videoUseExoplayer = new BooleanSetting(p, "preference_video_exoplayer", true);
        textOnly = new BooleanSetting(p, "preference_text_only", false);
        videoErrorIgnore = new BooleanSetting(p, "preference_video_error_ignore", false);
        boardViewMode = new OptionsSetting<>(p, "preference_board_view_mode", PostViewMode.class, PostViewMode.LIST);
        boardGridSpanCount = new IntegerSetting(p, "preference_board_grid_span_count", 0);
        boardOrder = new StringSetting(p, "preference_board_order", PostsFilter.Order.BUMP.name);

        postDefaultName = new StringSetting(p, "preference_default_name", "");
        postPinThread = new BooleanSetting(p, "preference_pin_on_post", false);

        developer = new BooleanSetting(p, "preference_developer", false);

        saveLocation = new StringSetting(p, "preference_image_save_location", Environment.getExternalStorageDirectory() + File.separator + "Clover");
        saveLocation.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(saveLocation)));
        saveOriginalFilename = new BooleanSetting(p, "preference_image_save_original", false);
        shareUrl = new BooleanSetting(p, "preference_image_share_url", false);
        enableReplyFab = new BooleanSetting(p, "preference_enable_reply_fab", true);
        anonymize = new BooleanSetting(p, "preference_anonymize", false);
        anonymizeIds = new BooleanSetting(p, "preference_anonymize_ids", false);
        showAnonymousName = new BooleanSetting(p, "preference_show_anonymous_name", false);
        revealImageSpoilers = new BooleanSetting(p, "preference_reveal_image_spoilers", false);
        revealTextSpoilers = new BooleanSetting(p, "preference_reveal_text_spoilers", false);
        repliesButtonsBottom = new BooleanSetting(p, "preference_buttons_bottom", false);
        confirmExit = new BooleanSetting(p, "preference_confirm_exit", false);
        tapNoReply = new BooleanSetting(p, "preference_tap_no_reply", false);
        volumeKeysScrolling = new BooleanSetting(p, "preference_volume_key_scrolling", false);
        postFullDate = new BooleanSetting(p, "preference_post_full_date", false);
        postFileInfo = new BooleanSetting(p, "preference_post_file_info", true);
        postFilename = new BooleanSetting(p, "preference_post_filename", true);
        neverHideToolbar = new BooleanSetting(p, "preference_never_hide_toolbar", false);
        controllerSwipeable = new BooleanSetting(p, "preference_controller_swipeable", true);
        saveBoardFolder = new BooleanSetting(p, "preference_save_subboard", false);
        videoDefaultMuted = new BooleanSetting(p, "preference_video_default_muted", true);
        videoAutoLoop = new BooleanSetting(p, "preference_video_loop", true);

        watchEnabled = new BooleanSetting(p, "preference_watch_enabled", false);
        watchEnabled.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchEnabled)));
        watchCountdown = new BooleanSetting(p, "preference_watch_countdown", false);
        watchBackground = new BooleanSetting(p, "preference_watch_background_enabled", false);
        watchBackground.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchBackground)));
        watchBackgroundInterval = new IntegerSetting(p, "preference_watch_background_interval", WatchManager.DEFAULT_BACKGROUND_INTERVAL);
        watchNotifyMode = new StringSetting(p, "preference_watch_notify_mode", "all");
        watchSound = new StringSetting(p, "preference_watch_sound", "quotes");
        watchPeek = new BooleanSetting(p, "preference_watch_peek", true);
        watchLed = new StringSetting(p, "preference_watch_led", "ffffffff");

        historyEnabled = new BooleanSetting(p, "preference_history_enabled", true);

        previousVersion = new IntegerSetting(p, "preference_previous_version", 0);

        proxyEnabled = new BooleanSetting(p, "preference_proxy_enabled", false);
        proxyAddress = new StringSetting(p, "preference_proxy_address", "");
        proxyPort = new IntegerSetting(p, "preference_proxy_port", 80);

        proxyEnabled.addCallback((setting, value) -> loadProxy());
        proxyAddress.addCallback((setting, value) -> loadProxy());
        proxyPort.addCallback((setting, value) -> loadProxy());
        loadProxy();

        historyOpenCounter = new CounterSetting(p, "counter_history_open");
        threadOpenCounter = new CounterSetting(p, "counter_thread_open");

        updateCheckTime = new LongSetting(p, "update_check_time", 0L);
        updateCheckInterval = new LongSetting(p, "update_check_interval", UpdateManager.DEFAULT_UPDATE_CHECK_INTERVAL_MS);

        crashReporting = new BooleanSetting(p, "preference_crash_reporting", true);

        /**
         *  DO NOT CHANGE THE ORDER!!!
         *  JUST ADD NEW SETTINGS AT THE END!!!
         */

        // To make serializing/deserializing more convenient
        allSettings.add(forceEnglishLocale);
        allSettings.add(theme);
        allSettings.add(layoutMode);
        allSettings.add(fontSize);
        allSettings.add(fontCondensed);
        allSettings.add(openLinkConfirmation);
        allSettings.add(openLinkBrowser);
        allSettings.add(autoRefreshThread);
        allSettings.add(imageAutoLoadNetwork);
        allSettings.add(videoAutoLoadNetwork);
        allSettings.add(videoOpenExternal);
        allSettings.add(videoUseExoplayer);
        allSettings.add(textOnly);
        allSettings.add(videoErrorIgnore);
        allSettings.add(boardViewMode);
        allSettings.add(boardGridSpanCount);
        allSettings.add(boardOrder);
        allSettings.add(postDefaultName);
        allSettings.add(postPinThread);
        allSettings.add(developer);
        allSettings.add(saveLocation);
        allSettings.add(saveOriginalFilename);
        allSettings.add(shareUrl);
        allSettings.add(enableReplyFab);
        allSettings.add(anonymize);
        allSettings.add(anonymizeIds);
        allSettings.add(showAnonymousName);
        allSettings.add(revealImageSpoilers);
        allSettings.add(revealTextSpoilers);
        allSettings.add(repliesButtonsBottom);
        allSettings.add(confirmExit);
        allSettings.add(tapNoReply);
        allSettings.add(volumeKeysScrolling);
        allSettings.add(postFullDate);
        allSettings.add(postFileInfo);
        allSettings.add(postFilename);
        allSettings.add(neverHideToolbar);
        allSettings.add(controllerSwipeable);
        allSettings.add(saveBoardFolder);
        allSettings.add(videoDefaultMuted);
        allSettings.add(videoAutoLoop);
        allSettings.add(watchEnabled);
        allSettings.add(watchCountdown);
        allSettings.add(watchBackground);
        allSettings.add(watchBackgroundInterval);
        allSettings.add(watchNotifyMode);
        allSettings.add(watchSound);
        allSettings.add(watchPeek);
        allSettings.add(watchLed);
        allSettings.add(historyEnabled);
        allSettings.add(previousVersion);
        allSettings.add(proxyEnabled);
        allSettings.add(proxyAddress);
        allSettings.add(proxyPort);
        allSettings.add(historyOpenCounter);
        allSettings.add(threadOpenCounter);
        allSettings.add(updateCheckTime);
        allSettings.add(updateCheckInterval);
        allSettings.add(crashReporting);

        // Old (but possibly still in some users phone)
        // preference_board_view_mode default "list"
        // preference_board_editor_filler default false
        // preference_pass_enabled default false
        // preference_autoplay false
        // preference_watch_background_timeout "60" the old timeout background setting in minutes
        // preference_network_https true
        // counter_settings_open
        // counter_reply_open
    }

    public static boolean isCrashReportingAvailable() {
        return !BuildConfig.CRASH_REPORT_ENDPOINT.isEmpty();
    }

    public static boolean isCrashReportingEnabled() {
        return isCrashReportingAvailable() && crashReporting.get();
    }

    public static ThemeColor getThemeAndColor() {
        String themeRaw = ChanSettings.theme.get();

        String theme = themeRaw;
        String color = null;
        String accentColor = null;

        String[] splitted = themeRaw.split(",");
        if (splitted.length >= 2) {
            theme = splitted[0];
            color = splitted[1];
            if (splitted.length == 3) {
                accentColor = splitted[2];
            }
        }

        return new ThemeColor(theme, color, accentColor);
    }

    public static void setThemeAndColor(ThemeColor themeColor) {
        if (TextUtils.isEmpty(themeColor.color) || TextUtils.isEmpty(themeColor.accentColor)) {
            throw new IllegalArgumentException();
        }
        ChanSettings.theme.set(themeColor.theme + "," + themeColor.color + "," + themeColor.accentColor);
    }

    /**
     * Returns a {@link Proxy} if a proxy is enabled, <tt>null</tt> otherwise.
     *
     * @return a proxy or null
     */
    public static Proxy getProxy() {
        return proxy;
    }

    private static void loadProxy() {
        if (proxyEnabled.get()) {
            proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyAddress.get(), proxyPort.get()));
        } else {
            proxy = null;
        }
    }

    /**
     * Reads setting from the shared preferences to a string.
     * Called on the Database thread.
     * */
    public static String serializeToString() {
        StringBuilder sb = new StringBuilder(512);
        SettingProvider p = new SharedPreferencesSettingProvider(AndroidUtils.getPreferences());

        for (Setting<?> setting : allSettings) {
            if (setting instanceof BooleanSetting) {
                boolean value = p.getBoolean(setting.key, ((BooleanSetting) setting).def);

                sb.append("(");
                sb.append(setting.key);
                sb.append(":");
                sb.append(value);
                sb.append(")");
                sb.append(SETTINGS_SEPARATOR);
            } else if (setting instanceof IntegerSetting) {
                int value = p.getInt(setting.key, ((IntegerSetting) setting).def);

                sb.append("(");
                sb.append(setting.key);
                sb.append(":");
                sb.append(value);
                sb.append(")");
                sb.append(SETTINGS_SEPARATOR);
            } else if (setting instanceof LongSetting) {
                long value = p.getLong(setting.key, ((LongSetting) setting).def);

                sb.append("(");
                sb.append(setting.key);
                sb.append(":");
                sb.append(value);
                sb.append(")");
                sb.append(SETTINGS_SEPARATOR);
            } else if (setting instanceof StringSetting) {
                String value = p.getString(setting.key, ((StringSetting) setting).def);

                sb.append("(");
                sb.append(setting.key);
                sb.append(":");
                sb.append(value);
                sb.append(")");
                sb.append(SETTINGS_SEPARATOR);
            } else if (setting instanceof OptionsSetting<?>) {
                String value = serializeOptionSetting(p, (OptionsSetting<?>) setting);

                sb.append("(");
                sb.append(setting.key);
                sb.append(":");
                sb.append(value);
                sb.append(")");
                sb.append(SETTINGS_SEPARATOR);
            }
        }

        if (sb.length() > 2) {
            return sb.deleteCharAt(sb.length() - SETTINGS_SEPARATOR.length()).toString();
        }

        return sb.toString();
    }

    /**
     * Convenient method for reading Enum settings from the shared preferences
     * */
    private static <T extends Enum & OptionSettingItem> String serializeOptionSetting(
            SettingProvider settingProvider,
            OptionsSetting<T> setting
    ) {
        for (T item : setting.getItems()) {
            if (item.name().equals(setting.get().name())) {
                return settingProvider.getString(setting.getKey(), setting.getDefault().name());
            }
        }

        throw new IllegalStateException("Could not find setting value for OptionsSetting");
    }

    /**
     * Reads settings from string and writes them to the shared preferences.
     * Called on the Database thread.
     */
    public static void deserializeFromString(String settings) {
        String[] settingsArray = settings.split(SETTINGS_SEPARATOR);
        if (settingsArray.length == 0) {
            return;
        }

        int size = Math.min(settingsArray.length, allSettings.size());
        SharedPreferencesSettingProvider p = new SharedPreferencesSettingProvider(AndroidUtils.getPreferences());

        for (int i = 0; i < size; ++i) {
            Setting<?> setting = allSettings.get(i);
            String rawSetting = settingsArray[i];

            // just skip a setting if it's not expected to be here
            if (rawSetting.contains(setting.key)) {
                String[] keyValue = rawSetting.substring(1, rawSetting.length() - 1).split(":");
                if (keyValue.length != 2) {
                    return;
                }

                String value = keyValue[1];

                // We need to commit to shared prefs to avoid memory caching because there won't be
                // enough time for shared prefs to dump changes to disk since we restart the app
                // right away after this operation completes

                //TODO: figure out whether it's possible to use apply and then use commit once
                // in the end to write changes to the disk
                if (setting instanceof BooleanSetting) {
                    p.putBooleanSync(setting.key, Boolean.valueOf(value));
                } else if (setting instanceof IntegerSetting) {
                    p.putIntSync(setting.key, Integer.valueOf(value));
                } else if (setting instanceof LongSetting) {
                    p.putLongSync(setting.key, Long.valueOf(value));
                } else if (setting instanceof StringSetting) {
                    p.putStringSync(setting.key, value);
                } else if (setting instanceof OptionsSetting<?>) {
                    deserializeOptionSetting(p, (OptionsSetting<?>) setting, value);
                }
            }
        }
    }

    /**
     * Convenient method for writing Enum settings to the shared preferences
     * */
    private static <T extends Enum & OptionSettingItem> void deserializeOptionSetting(
            SharedPreferencesSettingProvider settingProvider,
            OptionsSetting<T> setting,
            String value
    ) {
        for (T item : setting.getItems()) {
            if (item.getKey().equals(value)) {
                settingProvider.putStringSync(setting.key, item.getKey());
                return;
            }
        }
    }

    public static class ThemeColor {
        public String theme;
        public String color;
        public String accentColor;

        public ThemeColor(String theme, String color, String accentColor) {
            this.theme = theme;
            this.color = color;
            this.accentColor = accentColor;
        }
    }

    public static class SettingChanged<T> {
        public final Setting<T> setting;

        public SettingChanged(Setting<T> setting) {
            this.setting = setting;
        }
    }
}
