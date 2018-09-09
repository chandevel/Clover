/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014 Floens
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
package org.floens.chan;

import android.content.Context;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraHttpSender;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;
import org.floens.chan.core.settings.ChanSettings;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_CODE;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.AVAILABLE_MEM_SIZE;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.BUILD;
import static org.acra.ReportField.BUILD_CONFIG;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.MEDIA_CODEC_LIST;
import static org.acra.ReportField.PACKAGE_NAME;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.REPORT_ID;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.TOTAL_MEM_SIZE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;

/**
 * The ChanApplication belonging to the release configuration.
 * <p>
 * It has acra enabled. Acra unfortunately believes it needs to have annotations on our
 * application class, which I find really intrusive. We already had ChanApplication pointing to
 * Chan, so we now have a debug and release variant of ChanApplication. The real initialization
 * is done with the {@link Chan#initialize()} method, which does not get called from acra
 * processes.
 */
@AcraCore(
        sharedPreferencesName = "acra_preferences",
        alsoReportToAndroidFramework = true,
        buildConfigClass = BuildConfig.class,
        reportFormat = StringFormat.JSON,
        reportContent = {
                // Required
                REPORT_ID,

                // What app version
                APP_VERSION_CODE,
                APP_VERSION_NAME,
                PACKAGE_NAME,
                BUILD_CONFIG,

                // What phone
                PHONE_MODEL,
                BRAND,
                PRODUCT,

                // What Android version
                ANDROID_VERSION,
                BUILD,

                // Memory details
                TOTAL_MEM_SIZE,
                AVAILABLE_MEM_SIZE,

                // Useful for webm debugging
                MEDIA_CODEC_LIST,

                // The error
                STACK_TRACE,
                LOGCAT,
                USER_APP_START_DATE,
                USER_CRASH_DATE
        }
)
@AcraHttpSender(
        httpMethod = HttpSender.Method.PUT,
        uri = BuildConfig.CRASH_REPORT_ENDPOINT
)
public class ChanApplication extends Chan {
    @Override
    public void onCreate() {
        super.onCreate();

        // Do not initialize again if running from the crash reporting process.
        if (!ACRA.isACRASenderServiceProcess()) {
            initialize();
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (enableAcra()) {
            ACRA.init(this);
        }
    }

    private boolean enableAcra() {
        return ChanSettings.isCrashReportingEnabled();
    }
}
