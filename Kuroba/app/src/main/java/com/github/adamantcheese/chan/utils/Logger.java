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
package com.github.adamantcheese.chan.utils;

import android.util.Log;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

/**
 * This class wraps Log calls for the application. Priority levels are otherwise identical.
 * The order in terms of verbosity, from least to most is ERROR, WARN, INFO, DEBUG.
 * ERROR is in case of severe errors that need to be addressed
 * WARN is in case of non-severe errors that don't need to be addressed
 * INFO is informational for the user
 * DEBUG is informational for the developer
 * Verbose debug and error are special cases that allow for them to be turned off in the application.
 * Regular verbose messages have been removed in favor of the verbose debug capability.
 */
@SuppressWarnings("unused")
public class Logger {
    //region ERROR
    public static void e(Object source, String message) {
        Log.e(getTag(source), message);
    }

    public static void e(Object source, String message, Throwable throwable) {
        Log.e(getTag(source), message, throwable);
    }

    public static void e(Object source, String format, Object... args) {
        Log.e(getTag(source), String.format(format, args));
    }
    //endregion ERROR

    //region VERBOSE ERROR
    public static void ve(Object source, String message) {
        if (ChanSettings.verboseLogs.get()) {
            Log.e(getTag(source), message);
        }
    }

    public static void ve(Object source, String message, Throwable throwable) {
        if (ChanSettings.verboseLogs.get()) {
            Log.e(getTag(source), message, throwable);
        }
    }

    public static void ve(Object source, String format, Object... args) {
        if (ChanSettings.verboseLogs.get()) {
            Log.e(getTag(source), String.format(format, args));
        }
    }
    //endregion ERROR

    //region WARN
    public static void w(Object source, String message) {
        Log.w(getTag(source), message);
    }

    public static void w(Object source, String message, Throwable throwable) {
        Log.w(getTag(source), message, throwable);
    }

    public static void w(Object source, String format, Object... args) {
        Log.w(getTag(source), String.format(format, args));
    }
    //endregion WARN

    //region INFO
    public static void i(Object source, String message) {
        Log.i(getTag(source), message);
    }

    public static void i(Object source, String message, Throwable throwable) {
        Log.i(getTag(source), message, throwable);
    }

    public static void i(Object source, String format, Object... args) {
        Log.i(getTag(source), String.format(format, args));
    }
    //endregion INFO

    //region DEBUG
    public static void d(Object source, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(getTag(source), message);
        }
    }

    public static void d(Object source, String message, Throwable throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(getTag(source), message, throwable);
        }
    }

    public static void d(Object source, String format, Object... args) {
        if (BuildConfig.DEBUG) {
            Log.d(getTag(source), String.format(format, args));
        }
    }
    //endregion DEBUG

    //region VERBOSE DEBUG
    public static void vd(Object source, String message) {
        if (ChanSettings.verboseLogs.get() && BuildConfig.DEBUG) {
            Log.d(getTag(source), message);
        }
    }

    public static void vd(Object source, String message, Throwable throwable) {
        if (ChanSettings.verboseLogs.get() && BuildConfig.DEBUG) {
            Log.d(getTag(source), message, throwable);
        }
    }

    public static void vd(Object source, String format, Object... args) {
        if (ChanSettings.verboseLogs.get() && BuildConfig.DEBUG) {
            Log.d(getTag(source), String.format(format, args));
        }
    }
    //endregion DEBUG

    //region WTF
    public static void wtf(Object source, String message) {
        Log.wtf(getTag(source), message);
    }

    public static void wtf(Object source, String message, Throwable throwable) {
        Log.wtf(getTag(source), message, throwable);
    }

    public static void wtf(Object source, String format, Object... args) {
        Log.wtf(getTag(source), String.format(format, args));
    }
    //endregion WTF

    private static String getTag(Object o) {
        String tagPrefix = BuildConfig.APP_LABEL + " | ";
        if (o instanceof String) return tagPrefix + o;
        return tagPrefix + o.getClass().getSimpleName();
    }
}
