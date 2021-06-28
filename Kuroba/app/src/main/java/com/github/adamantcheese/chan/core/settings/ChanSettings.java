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
package com.github.adamantcheese.chan.core.settings;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.base_dir.SavedFilesBaseDirSetting;
import com.github.adamantcheese.chan.core.settings.primitives.BooleanSetting;
import com.github.adamantcheese.chan.core.settings.primitives.IntegerSetting;
import com.github.adamantcheese.chan.core.settings.primitives.OptionSettingItem;
import com.github.adamantcheese.chan.core.settings.primitives.OptionsSetting;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting;
import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;
import com.github.adamantcheese.chan.core.settings.provider.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppDir;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getPreferences;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getScreenOrientation;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isConnected;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * This settings class is for all persistable settings that should be saved as preferences. Note that all settings in here
 * will be exported when a backup is exported; for persistable application data that SHOULDN'T be exported, use
 * {@link PersistableChanState} to store that data.
 */

public class ChanSettings {
    public static final String EMPTY_JSON = "{}";

    //region Setting Enums
    public enum WatchNotifyMode
            implements OptionSettingItem {
        NOTIFY_ALL_POSTS("all"),
        NOTIFY_ONLY_QUOTES("quotes");

        String mode;

        WatchNotifyMode(String mode) {
            this.mode = mode;
        }

        @Override
        public String getKey() {
            return mode;
        }
    }

    public enum MediaAutoLoadMode
            implements OptionSettingItem {
        // ALways auto load, either wifi or mobile
        ALL,
        // Only auto load if on wifi
        WIFI,
        // Never auto load
        NONE;

        @Override
        public String getKey() {
            return name().toLowerCase();
        }

        public static boolean shouldLoadForNetworkType(ChanSettings.MediaAutoLoadMode networkType) {
            if (networkType == ChanSettings.MediaAutoLoadMode.NONE) {
                return false;
            } else if (networkType == ChanSettings.MediaAutoLoadMode.WIFI) {
                return isConnected(ConnectivityManager.TYPE_WIFI);
            } else {
                return networkType == ChanSettings.MediaAutoLoadMode.ALL;
            }
        }
    }

    public enum PostViewMode
            implements OptionSettingItem {
        LIST,
        GRID;

        @Override
        public String getKey() {
            return name().toLowerCase();
        }
    }

    public enum LayoutMode
            implements OptionSettingItem {
        AUTO,
        PHONE,
        SLIDE,
        SPLIT;

        @Override
        public String getKey() {
            return name().toLowerCase();
        }
    }

    public enum ImageClickPreloadStrategy
            implements OptionSettingItem {
        PreloadNext("Preload next image"),
        PreloadPrevious("Preload previous image"),
        PreloadBoth("Preload next and previous images"),
        PreloadNeither("Do not preload any images");

        String name;

        ImageClickPreloadStrategy(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    public enum ProxyMode
            implements OptionSettingItem {
        HTTP(Proxy.Type.HTTP),
        SOCKS(Proxy.Type.SOCKS);

        Proxy.Type type;

        ProxyMode(Proxy.Type type) {
            this.type = type;
        }

        @Override
        public String getKey() {
            return type.name().toLowerCase();
        }
    }
    //endregion

    public static Proxy proxy;
    private static final String sharedPrefsFile = "shared_prefs/" + BuildConfig.APPLICATION_ID + "_preferences.xml";

    //region Declarations
    //region THREAD WATCHER
    public static final BooleanSetting watchEnabled;
    public static final BooleanSetting watchBackground;
    public static final IntegerSetting watchBackgroundInterval;
    public static final BooleanSetting removeWatchedFromCatalog;
    public static final BooleanSetting watchLastPageNotify;
    public static final OptionsSetting<WatchNotifyMode> watchNotifyMode;
    public static final OptionsSetting<WatchNotifyMode> watchSound;
    public static final BooleanSetting watchPeek;
    //endregion

    //region APPEARANCE
    // Theme
    public static final StringSetting themeDay;
    public static final StringSetting themeNight;

    // Layout
    public static final OptionsSetting<LayoutMode> layoutMode;
    public static final IntegerSetting boardGridSpanCountPortrait;
    public static final IntegerSetting albumGridSpanCountPortrait;
    public static final IntegerSetting boardGridSpanCountLandscape;
    public static final IntegerSetting albumGridSpanCountLandscape;
    public static final BooleanSetting neverHideToolbar;
    public static final BooleanSetting alwaysShowPostOptions;
    public static final BooleanSetting enableReplyFab;
    public static final BooleanSetting moveInputToBottom;
    public static final BooleanSetting captchaOnBottom;
    public static final BooleanSetting reverseDrawer;
    public static final BooleanSetting useImmersiveModeForGallery;
    public static final BooleanSetting moveSortToToolbar;
    public static final BooleanSetting neverShowPages;

    //Post
    public static final IntegerSetting thumbnailSize;
    public static final IntegerSetting fontSize;
    public static final BooleanSetting fontAlternate;
    public static final BooleanSetting shiftPostFormat;
    public static final BooleanSetting accessibleInfo;
    public static final BooleanSetting postFullDate;
    public static final BooleanSetting postFileInfo;
    public static final BooleanSetting postFilename;
    public static final BooleanSetting textOnly;
    public static final BooleanSetting revealTextSpoilers;
    public static final BooleanSetting anonymize;
    public static final BooleanSetting showAnonymousName;
    public static final BooleanSetting anonymizeIds;
    public static final BooleanSetting addDubs;
    public static final BooleanSetting enableEmbedding;
    public static final BooleanSetting enableEmoji;
    public static final BooleanSetting parseExtraQuotes;
    public static final BooleanSetting parseExtraSpoilers;
    public static final BooleanSetting mildMarkdown;

    // Images
    public static final BooleanSetting hideImages;
    public static final BooleanSetting removeImageSpoilers;
    public static final BooleanSetting revealimageSpoilers;
    public static final BooleanSetting parsePostImageLinks;
    public static final BooleanSetting useOpaqueBackgrounds;
    public static final BooleanSetting opacityMenuItem;
    public static final BooleanSetting neverShowAlbumCellInfo;

    // Set elsewhere in the application
    public static final OptionsSetting<PostViewMode> boardViewMode;
    public static final StringSetting boardOrder;
    //endregion

    //region BEHAVIOUR
    // General
    public static final BooleanSetting autoRefreshThread;
    public static final BooleanSetting controllerSwipeable;
    public static final BooleanSetting openLinkConfirmation;
    public static final BooleanSetting openLinkBrowser;
    public static final BooleanSetting imageViewerGestures;
    public static final BooleanSetting alwaysOpenDrawer;
    public static final BooleanSetting applyImageFilterToPost;

    // Reply
    public static final BooleanSetting postPinThread;
    public static final StringSetting postDefaultName;
    public static final BooleanSetting alwaysSetNewFilename;

    // Post
    public static final BooleanSetting repliesButtonsBottom;
    public static final BooleanSetting volumeKeysScrolling;
    public static final BooleanSetting enableLongPressURLCopy;
    public static final BooleanSetting shareUrl;

    // Other options
    public static final BooleanSetting fullUserRotationEnable;
    public static final BooleanSetting allowFilePickChooser;

    // Proxy
    public static final BooleanSetting proxyEnabled;
    public static final StringSetting proxyAddress;
    public static final IntegerSetting proxyPort;
    public static final OptionsSetting<ProxyMode> proxyType;
    //endregion

    //region MEDIA
    // Saving
    public static final SavedFilesBaseDirSetting saveLocation;
    public static final BooleanSetting saveImageBoardFolder;
    public static final BooleanSetting saveImageThreadFolder;
    public static final BooleanSetting saveAlbumBoardFolder;
    public static final BooleanSetting saveAlbumThreadFolder;
    public static final BooleanSetting saveServerFilename;

    // Video settings
    public static final BooleanSetting videoAutoLoop;
    public static final BooleanSetting videoDefaultMuted;
    public static final BooleanSetting headsetDefaultMuted;
    public static final BooleanSetting neverShowWebmControls;
    public static final BooleanSetting enableSoundposts;

    // Media loading
    public static final OptionsSetting<MediaAutoLoadMode> imageAutoLoadNetwork;
    public static final OptionsSetting<MediaAutoLoadMode> videoAutoLoadNetwork;
    public static final OptionsSetting<ImageClickPreloadStrategy> imageClickPreloadStrategy;
    public static final BooleanSetting autoLoadThreadImages;
    public static final IntegerSetting fileCacheSize;
    //endregion

    //region EXPERIMENTAL
    public static final StringSetting androidTenGestureZones;
    public static final BooleanSetting okHttpAllowHttp2;
    public static final BooleanSetting okHttpAllowIpv6;
    //endregion

    //region OTHER
    public static final BooleanSetting collectCrashLogs;
    //endregion

    //region DEVELOPER
    public static final BooleanSetting debugFilters;
    public static final BooleanSetting crashOnWrongThread;
    public static final BooleanSetting verboseLogs;
    //endregion

    //region DATA
    // While not a setting, the last image options selected should be persisted even after import.
    public static final StringSetting lastImageOptions;
    //endregion
    //endregion

    static {
        try {
            SettingProvider<Object> p = new SharedPreferencesSettingProvider(getPreferences());

            //region THREAD WATCHER
            watchEnabled = new BooleanSetting(p, "preference_watch_enabled", false);
            watchEnabled.addCallback(new EventBusCallback<>(watchEnabled));
            watchBackground = new BooleanSetting(p, "preference_watch_background_enabled", false);
            watchBackground.addCallback(new EventBusCallback<>(watchBackground));
            watchBackgroundInterval =
                    new IntegerSetting(p, "preference_watch_background_interval", (int) MINUTES.toMillis(15));
            watchBackgroundInterval.addCallback(new EventBusCallback<>(watchBackgroundInterval));
            removeWatchedFromCatalog = new BooleanSetting(p, "remove_catalog_watch", false);
            watchLastPageNotify = new BooleanSetting(p, "preference_watch_last_page_notify", false);
            watchNotifyMode = new OptionsSetting<>(p,
                    "preference_watch_notify_mode",
                    WatchNotifyMode.class,
                    WatchNotifyMode.NOTIFY_ALL_POSTS
            );
            watchSound = new OptionsSetting<>(p,
                    "preference_watch_sound",
                    WatchNotifyMode.class,
                    WatchNotifyMode.NOTIFY_ONLY_QUOTES
            );
            watchPeek = new BooleanSetting(p, "preference_watch_peek", true);
            //endregion

            //region APPEARANCE
            // Theme
            themeDay = new StringSetting(p, "preference_theme", ThemeHelper.defaultDayTheme.toString());
            themeNight = new StringSetting(p, "preference_theme_2", ThemeHelper.defaultNightTheme.toString());

            //Layout
            layoutMode = new OptionsSetting<>(p, "preference_layout_mode", LayoutMode.class, LayoutMode.AUTO);
            boardGridSpanCountPortrait = new IntegerSetting(p, "preference_board_grid_span_count", 0);
            albumGridSpanCountPortrait = new IntegerSetting(p, "preference_album_grid_span_count", 0);
            boardGridSpanCountLandscape = new IntegerSetting(p, "preference_board_grid_span_count_landscape", 0);
            albumGridSpanCountLandscape = new IntegerSetting(p, "preference_album_grid_span_count_landscape", 0);
            neverHideToolbar = new BooleanSetting(p, "preference_never_hide_toolbar", false);
            alwaysShowPostOptions = new BooleanSetting(p, "preference_always_show_post_options", false);
            enableReplyFab = new BooleanSetting(p, "preference_enable_reply_fab", true);
            moveInputToBottom = new BooleanSetting(p, "move_input_bottom", false);
            captchaOnBottom = new BooleanSetting(p, "captcha_on_bottom", true);
            reverseDrawer = new BooleanSetting(p, "reverse_drawer", false);
            useImmersiveModeForGallery = new BooleanSetting(p, "use_immersive_mode_for_gallery", false);
            moveSortToToolbar = new BooleanSetting(p, "move_sort_to_toolbar", false);
            neverShowPages = new BooleanSetting(p, "never_show_page_number", false);

            // Post
            thumbnailSize = new IntegerSetting(p, "preference_thumbnail", 100);
            thumbnailSize.addCallback((s, v) -> resetThumbnailCacheSize());
            fontSize = new IntegerSetting(p, "preference_font", getRes().getBoolean(R.bool.is_tablet) ? 16 : 14);
            fontAlternate = new BooleanSetting(p, "preference_font_alternate", false);
            shiftPostFormat = new BooleanSetting(p, "shift_post_format", true);
            accessibleInfo = new BooleanSetting(p, "preference_enable_accessible_info", false);
            postFullDate = new BooleanSetting(p, "preference_post_full_date", false);
            postFileInfo = new BooleanSetting(p, "preference_post_file_info", true);
            postFilename = new BooleanSetting(p, "preference_post_filename", true);
            textOnly = new BooleanSetting(p, "preference_text_only", false);
            revealTextSpoilers = new BooleanSetting(p, "preference_reveal_text_spoilers", false);
            anonymize = new BooleanSetting(p, "preference_anonymize", false);
            showAnonymousName = new BooleanSetting(p, "preference_show_anonymous_name", false);
            anonymizeIds = new BooleanSetting(p, "preference_anonymize_ids", false);
            addDubs = new BooleanSetting(p, "add_dubs", false);
            enableEmbedding = new BooleanSetting(p, "parse_media_titles", true);
            enableEmoji = new BooleanSetting(p, "enable_emoji", false);
            parseExtraQuotes = new BooleanSetting(p, "parse_extra_quotes", false);
            parseExtraSpoilers = new BooleanSetting(p, "parse_extra_spoilers", false);
            mildMarkdown = new BooleanSetting(p, "parse_markdown_subset", false);

            // Images
            hideImages = new BooleanSetting(p, "preference_hide_images", false);
            removeImageSpoilers = new BooleanSetting(p, "preference_reveal_image_spoilers", false);
            revealimageSpoilers = new BooleanSetting(p, "preference_auto_unspoil_images", true);
            parsePostImageLinks = new BooleanSetting(p, "parse_post_image_links", true);
            useOpaqueBackgrounds = new BooleanSetting(p, "image_transparency_on", false);
            opacityMenuItem = new BooleanSetting(p, "opacity_menu_item", false);
            neverShowAlbumCellInfo = new BooleanSetting(p, "never_show_album_cell_info", false);

            //Elsewhere
            boardViewMode =
                    new OptionsSetting<>(p, "preference_board_view_mode", PostViewMode.class, PostViewMode.LIST);
            boardOrder = new StringSetting(p, "preference_board_order", PostsFilter.Order.BUMP.name().toLowerCase());
            //endregion

            //region BEHAVIOUR
            // General
            autoRefreshThread = new BooleanSetting(p, "preference_auto_refresh_thread", true);
            controllerSwipeable = new BooleanSetting(p, "preference_controller_swipeable", true);
            openLinkConfirmation = new BooleanSetting(p, "preference_open_link_confirmation", false);
            openLinkBrowser = new BooleanSetting(p, "preference_open_link_browser", false);
            imageViewerGestures = new BooleanSetting(p, "image_viewer_gestures", true);
            alwaysOpenDrawer = new BooleanSetting(p, "drawer_auto_open_always", false);
            applyImageFilterToPost = new BooleanSetting(p, "apply_image_filtering_to_post", false);

            // Reply
            postPinThread = new BooleanSetting(p, "preference_pin_on_post", false);
            postDefaultName = new StringSetting(p, "preference_default_name", "");
            alwaysSetNewFilename = new BooleanSetting(p, "preference_always_set_new_filename", false);

            // Post
            repliesButtonsBottom = new BooleanSetting(p, "preference_buttons_bottom", false);
            volumeKeysScrolling = new BooleanSetting(p, "preference_volume_key_scrolling", false);
            enableLongPressURLCopy = new BooleanSetting(p, "long_press_image_url_copy", true);
            shareUrl = new BooleanSetting(p, "preference_image_share_url", false);

            // Other options
            fullUserRotationEnable = new BooleanSetting(p, "full_user_rotation_enable", true);
            allowFilePickChooser = new BooleanSetting(p, "allow_file_picker_chooser", false);

            // Proxy
            proxyEnabled = new BooleanSetting(p, "preference_proxy_enabled", false);
            proxyAddress = new StringSetting(p, "preference_proxy_address", "");
            proxyPort = new IntegerSetting(p, "preference_proxy_port", 80);
            proxyType = new OptionsSetting<>(p, "preference_proxy_type", ProxyMode.class, ProxyMode.HTTP);
            try {
                proxy = proxyEnabled.get()
                        ? new Proxy(proxyType.get().type,
                        InetSocketAddress.createUnresolved(proxyAddress.get(), proxyPort.get())
                )
                        : null;
            } catch (Exception e) {
                Logger.e("ChanSettings Proxy", "Failed to set up proxy! Using to OkHttp's default.", e);
                proxy = null;
            }
            //endregion

            //region MEDIA
            // Saving
            saveLocation = new SavedFilesBaseDirSetting(p);
            saveImageBoardFolder = new BooleanSetting(p, "preference_save_image_subboard", false);
            saveImageThreadFolder = new BooleanSetting(p, "preference_save_image_subthread", false);
            saveAlbumBoardFolder = new BooleanSetting(p, "preference_save_album_subboard", false);
            saveAlbumThreadFolder = new BooleanSetting(p, "preference_save_album_subthread", false);
            saveServerFilename = new BooleanSetting(p, "preference_image_save_original", false);

            // Video Settings
            videoAutoLoop = new BooleanSetting(p, "preference_video_loop", true);
            videoDefaultMuted = new BooleanSetting(p, "preference_video_default_muted", true);
            headsetDefaultMuted = new BooleanSetting(p, "preference_headset_default_muted", true);
            neverShowWebmControls = new BooleanSetting(p, "never_show_webm_controls", false);
            enableSoundposts = new BooleanSetting(p, "enable_soundposts", true);

            // Media loading
            imageAutoLoadNetwork = new OptionsSetting<>(p,
                    "preference_image_auto_load_network",
                    MediaAutoLoadMode.class,
                    MediaAutoLoadMode.WIFI
            );
            videoAutoLoadNetwork = new OptionsSetting<>(p,
                    "preference_video_auto_load_network",
                    MediaAutoLoadMode.class,
                    MediaAutoLoadMode.WIFI
            );
            imageClickPreloadStrategy = new OptionsSetting<>(p,
                    "image_click_preload_strategy",
                    ImageClickPreloadStrategy.class,
                    ImageClickPreloadStrategy.PreloadNext
            );
            autoLoadThreadImages = new BooleanSetting(p, "preference_auto_load_thread", false);
            fileCacheSize = new IntegerSetting(p, "preference_file_cache_size", 512);
            //endregion

            //region EXPERIMENTAL
            androidTenGestureZones = new StringSetting(p, "android_ten_gesture_zones", EMPTY_JSON);
            okHttpAllowHttp2 = new BooleanSetting(p, "ok_http_allow_http_2", true);
            okHttpAllowIpv6 = new BooleanSetting(p, "ok_http_allow_ipv6", true);
            //endregion

            //region OTHER
            collectCrashLogs = new BooleanSetting(p, "collect_crash_logs", true);
            //endregion

            //region DEVELOPER
            debugFilters = new BooleanSetting(p, "debug_filters", false);
            crashOnWrongThread = new BooleanSetting(p, "crash_on_wrong_thread", true);
            verboseLogs = new BooleanSetting(p, "verbose_logs", false);
            //endregion

            //region DATA
            lastImageOptions = new StringSetting(p, "last_image_options", "");
            //endregion

        } catch (Throwable error) {
            // If something crashes while the settings are initializing we at least will have the
            // stacktrace. Otherwise we won't because of Feather.
            Logger.e("ChanSettings", "Error while initializing the settings", error);
            throw error;
        }
    }

    public static int getBoardColumnCount() {
        return (getScreenOrientation() == ORIENTATION_PORTRAIT
                ? ChanSettings.boardGridSpanCountPortrait
                : ChanSettings.boardGridSpanCountLandscape).get();
    }

    public static int getAlbumColumnCount() {
        return (getScreenOrientation() == ORIENTATION_PORTRAIT
                ? ChanSettings.albumGridSpanCountPortrait
                : ChanSettings.albumGridSpanCountLandscape).get();
    }

    private static int thumbnailSizeCached = -1;

    public static int getThumbnailSize(Context c) {
        if (thumbnailSizeCached == -1) {
            thumbnailSizeCached =
                    getDimen(c, R.dimen.cell_post_thumbnail_size) * ChanSettings.thumbnailSize.get() / 100;
        }
        return thumbnailSizeCached;
    }

    private static void resetThumbnailCacheSize() {
        thumbnailSizeCached = -1;
    }

    public static boolean shouldUseFullSizeImage(PostImage postImage) {
        return ChanSettings.autoLoadThreadImages.get() && !postImage.isInlined;
    }

    /**
     * Reads setting from the shared preferences file to a string.
     * Called on the Database thread.
     */
    public static String serializeToString()
            throws IOException {
        String prevSaveLocationUri = null;

        /*
         We need to check if the user has any of the location settings set to a SAF directory.
         We can't export them because if the user reinstalls the app and then imports a location
         setting that point to a SAF directory that directory won't be valid for the app because
         after clearing settings all permissions for that directory will be lost. So in case the
         user tries to export SAF directory paths we don't export them and instead export default
         locations. But we also don't wont to change the paths for the current app so we need to
         save the previous paths, patch the sharedPrefs file read it to string and then restore
         the current paths back to what they were before exporting.

         We also need to reset the active dir setting in case of resetting the base dir (and then
         restore back) so that the user won't see empty paths to files when importing settings
         back.
        */
        if (saveLocation.isSafDirActive()) {
            // Save the saveLocationUri
            prevSaveLocationUri = saveLocation.getSafBaseDir().get();

            saveLocation.getSafBaseDir().remove();
            saveLocation.resetFileDir();
            saveLocation.resetActiveDir();
        }

        File file = new File(getAppDir(), sharedPrefsFile);

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
                throw new IOException("Could not read shared prefs file readAmount != fileLength " + readAmount + ", "
                        + file.length());
            }
        }

        // Restore back the previous paths
        if (prevSaveLocationUri != null) {
            ChanSettings.saveLocation.resetFileDir();
            ChanSettings.saveLocation.setSafBaseDir(Uri.parse(prevSaveLocationUri));
        }

        return new String(buffer);
    }

    /**
     * Reads settings from string and writes them to the shared preferences file.
     * Called on the Database thread.
     */
    public static void deserializeFromString(String settings)
            throws IOException {
        File file = new File(getAppDir(), sharedPrefsFile);

        if (!file.exists()) {
            // Hack to create the shared_prefs file when it does not exist so that we don't cancel
            // settings importing because shared_prefs file does not exist
            int fontSize = ChanSettings.fontSize.get();
            ChanSettings.fontSize.setSyncNoCheck(fontSize);
        }

        if (!file.canWrite()) {
            throw new IOException("Cannot write to shared preferences file! (" + file.getAbsolutePath() + ")");
        }

        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(settings.getBytes());
            outputStream.flush();
        }
    }

    public static class SettingChanged<T> {
        public final Setting<T> setting;

        public SettingChanged(Setting<T> setting) {
            this.setting = setting;
        }
    }

    public static class EventBusCallback<T>
            extends SettingChanged<T>
            implements Setting.SettingCallback<T> {

        public EventBusCallback(Setting<T> setting) {
            super(setting);
        }

        @Override
        public void onValueChange(Setting<T> setting, T value) {
            postToEventBus(new SettingChanged<>(setting));
        }
    }
}
