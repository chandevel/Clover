/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
package com.github.adamantcheese.chan.core.settings;

import android.os.Environment;
import android.text.TextUtils;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

import de.greenrobot.event.EventBus;

public class ChanSettings {
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
    private static final String sharedPrefsFile = "shared_prefs/com.adamantcheese.github.chan_preferences.xml";

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
    public static final BooleanSetting enableYouCount;
    public static final BooleanSetting accessibleInfo;
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
    public static final BooleanSetting saveThreadFolder;
    public static final BooleanSetting videoDefaultMuted;
    public static final BooleanSetting videoAutoLoop;

    public static final BooleanSetting watchEnabled;
    public static final BooleanSetting watchBackground;
    public static final BooleanSetting watchFilterWatch;
    public static final BooleanSetting watchLastPageNotify;
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

    public static final BooleanSetting reencodeHintShown;

    public static final BooleanSetting crashReporting;
    public static final BooleanSetting useNewCaptchaWindow;
    public static final BooleanSetting useRealGoogleCookies;
    public static final StringSetting googleCookie;

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

        saveLocation = new StringSetting(p, "preference_image_save_location", Environment.getExternalStorageDirectory() + File.separator + "Clover4");
        saveLocation.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(saveLocation)));
        saveOriginalFilename = new BooleanSetting(p, "preference_image_save_original", false);
        shareUrl = new BooleanSetting(p, "preference_image_share_url", false);
        accessibleInfo = new BooleanSetting(p, "preference_enable_accessible_info", false);
        enableReplyFab = new BooleanSetting(p, "preference_enable_reply_fab", true);
        enableYouCount = new BooleanSetting(p, "preference_enable_you_count", false);
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
        saveThreadFolder = new BooleanSetting(p, "preference_save_subthread", false);
        videoDefaultMuted = new BooleanSetting(p, "preference_video_default_muted", true);
        videoAutoLoop = new BooleanSetting(p, "preference_video_loop", true);

        watchEnabled = new BooleanSetting(p, "preference_watch_enabled", false);
        watchEnabled.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchEnabled)));
        watchBackground = new BooleanSetting(p, "preference_watch_background_enabled", false);
        watchBackground.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchBackground)));
        watchLastPageNotify = new BooleanSetting(p, "preference_watch_last_page_notify", false);
        watchFilterWatch = new BooleanSetting(p, "preference_watch_filter_watch", false);
        watchFilterWatch.addCallback(((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchFilterWatch))));
        watchBackgroundInterval = new IntegerSetting(p, "preference_watch_background_interval", WakeManager.BACKGROUND_INTERVAL);
        watchBackgroundInterval.addCallback((setting, value) ->
                EventBus.getDefault().post(new SettingChanged<>(watchBackgroundInterval)));
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

        reencodeHintShown = new BooleanSetting(p, "preference_reencode_hint_already_shown", false);

        crashReporting = new BooleanSetting(p, "preference_crash_reporting", true);
        useNewCaptchaWindow = new BooleanSetting(p, "use_new_captcha_window", true);
        useRealGoogleCookies = new BooleanSetting(p, "use_real_google_cookies", false);
        googleCookie = new StringSetting(p, "google_cookie", "");
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
     * Reads setting from the shared preferences file to a string.
     * Called on the Database thread.
     */
    public static String serializeToString() throws IOException {
        File file = new File(AndroidUtils.getAppDir(), sharedPrefsFile);

        if (!file.exists()) {
            throw new IOException("Shared preferences file does not exist! (" + file.getAbsolutePath() + ")");
        }

        if (!file.canRead()) {
            throw new IOException("Cannot read from shared preferences file! (" + file.getAbsolutePath() + ")");
        }

        byte[] buffer = new byte[(int) file.length()];

        try (FileInputStream inputStream = new FileInputStream(file)) {
            int readAmount = inputStream.read(buffer);

            if (readAmount != file.length()) {
                throw new IOException("Could not read shared prefs file readAmount != fileLength " + readAmount + ", " + file.length());
            }
        }

        return new String(buffer);
    }

    /**
     * Reads settings from string and writes them to the shared preferences file.
     * Called on the Database thread.
     */
    public static void deserializeFromString(String settings) throws IOException {
        File file = new File(AndroidUtils.getAppDir(), sharedPrefsFile);

        if (!file.exists()) {
            throw new IOException("Shared preferences file does not exist! (" + file.getAbsolutePath() + ")");
        }

        if (!file.canWrite()) {
            throw new IOException("Cannot write to shared preferences file! (" + file.getAbsolutePath() + ")");
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(settings.getBytes());
            outputStream.flush();
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
