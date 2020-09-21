package com.github.adamantcheese.chan.core.settings;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.settings.primitives.BooleanSetting;
import com.github.adamantcheese.chan.core.settings.primitives.IntegerSetting;
import com.github.adamantcheese.chan.core.settings.primitives.LongSetting;
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting;
import com.github.adamantcheese.chan.core.settings.provider.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import static com.github.adamantcheese.chan.core.settings.ChanSettings.EMPTY_JSON;

/**
 * This state class acts in a similar manner to {@link ChanSettings}, but everything here is not exported; this data is
 * strictly for use internally to the application and acts as a helper to ensure that data is not lost.
 */

public class PersistableChanState {
    public static IntegerSetting watchLastCount;

    public static BooleanSetting hasNewApkUpdate;
    public static IntegerSetting previousVersion;
    public static LongSetting updateCheckTime;
    public static StringSetting previousDevHash;

    public static StringSetting filterWatchIgnored;
    public static StringSetting videoTitleDurCache;

    private static final String EMPTY_VALUE = "EMPTY_VALUE";

    static {
        try {
            SharedPreferencesSettingProvider p = new SharedPreferencesSettingProvider(AndroidUtils.getAppState());
            watchLastCount = new IntegerSetting(p, "watch_last_count", 0);

            hasNewApkUpdate = new BooleanSetting(p, "has_new_apk_update", false);
            previousVersion = new IntegerSetting(p, "previous_version", Integer.MIN_VALUE);
            updateCheckTime = new LongSetting(p, "update_check_time", 0L);
            previousDevHash = new StringSetting(p, "previous_dev_hash", EMPTY_VALUE);

            filterWatchIgnored = new StringSetting(p, "filter_watch_last_ignored_set", "");
            videoTitleDurCache = new StringSetting(p, "yt_cache", EMPTY_VALUE);

            // for any of the following persistables, ensure that these exist and are set to the following defaults
            // a call to get() does not forcibly generate these shared preferences and set their defaults
            if (previousVersion.get() == Integer.MIN_VALUE) {
                previousVersion.setSync(BuildConfig.VERSION_CODE);
            }

            if (EMPTY_VALUE.equals(previousDevHash.get())) {
                previousDevHash.setSync(BuildConfig.COMMIT_HASH);
            }

            if (EMPTY_VALUE.equals(videoTitleDurCache.get())) {
                videoTitleDurCache.setSync(EMPTY_JSON);
            }
        } catch (Exception e) {
            Logger.e("PersistableChanState", "Error while initializing the state", e);
            throw e;
        }
    }
}