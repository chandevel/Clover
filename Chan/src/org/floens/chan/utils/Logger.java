package org.floens.chan.utils;

import org.floens.chan.ChanApplication;

import android.util.Log;

public class Logger {
    private static final String TAG = "Chan";
    private static final String TAG_SPACER = " | ";

    public static void v(String tag, String message) {
        Log.v(TAG + TAG_SPACER + tag, message);
    }

    public static void v(String tag, String message, Throwable throwable) {
        Log.v(TAG + TAG_SPACER + tag, message, throwable);
    }

    public static void d(String tag, String message) {
        if (ChanApplication.DEVELOPER_MODE) {
            Log.d(TAG + TAG_SPACER + tag, message);
        }
    }

    public static void d(String tag, String message, Throwable throwable) {
        if (ChanApplication.DEVELOPER_MODE) {
            Log.d(TAG + TAG_SPACER + tag, message, throwable);
        }
    }

    public static void i(String tag, String message) {
        Log.i(TAG + TAG_SPACER + tag, message);
    }

    public static void i(String tag, String message, Throwable throwable) {
        Log.i(TAG + TAG_SPACER + tag, message, throwable);
    }

    public static void w(String tag, String message) {
        Log.w(TAG + TAG_SPACER + tag, message);
    }

    public static void w(String tag, String message, Throwable throwable) {
        Log.w(TAG + TAG_SPACER + tag, message, throwable);
    }

    public static void e(String tag, String message) {
        Log.e(TAG + TAG_SPACER + tag, message);
    }

    public static void e(String tag, String message, Throwable throwable) {
        Log.e(TAG + TAG_SPACER + tag, message, throwable);
    }

    public static void wtf(String tag, String message) {
        Log.wtf(TAG + TAG_SPACER + tag, message);
    }

    public static void wtf(String tag, String message, Throwable throwable) {
        Log.wtf(TAG + TAG_SPACER + tag, message, throwable);
    }

    public static void test(String message) {
        if (ChanApplication.DEVELOPER_MODE) {
            Log.i(TAG + TAG_SPACER + "test", message);
        }
    }

    public static void test(String message, Throwable throwable) {
        if (ChanApplication.DEVELOPER_MODE) {
            Log.i(TAG + TAG_SPACER + "test", message, throwable);
        }
    }
}
