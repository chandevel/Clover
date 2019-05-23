package com.github.adamantcheese.chan.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.util.Locale;

public class LocaleUtils {
    public static void overrideLocaleToEnglishIfNeeded(Context context) {
        if (ChanSettings.forceEnglishLocale.get()) {
            Resources resources = context.getResources();
            Configuration configuration = resources.getConfiguration();
            configuration.setLocale(Locale.ENGLISH);
            resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        }
    }
}
