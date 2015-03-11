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
package org.floens.chan.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AndroidUtils {
    public final static ViewGroup.LayoutParams MATCH_PARAMS = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    public final static ViewGroup.LayoutParams WRAP_PARAMS = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    public final static ViewGroup.LayoutParams MATCH_WRAP_PARAMS = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    public final static ViewGroup.LayoutParams WRAP_MATCH_PARAMS = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
    private static HashMap<String, Typeface> typefaceCache = new HashMap<>();

    public static Typeface ROBOTO_MEDIUM;
    public static Typeface ROBOTO_MEDIUM_ITALIC;

    public static void init() {
        ROBOTO_MEDIUM = getTypeface("Roboto-Medium.ttf");
        ROBOTO_MEDIUM_ITALIC = getTypeface("Roboto-MediumItalic.ttf");
    }

    public static Resources getRes() {
        return ChanApplication.con.getResources();
    }

    public static Context getAppRes() {
        return ChanApplication.con;
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(ChanApplication.con);
    }

    public static void openLink(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (intent.resolveActivity(getAppRes().getPackageManager()) != null) {
            getAppRes().startActivity(intent);
        } else {
            Toast.makeText(getAppRes(), R.string.open_link_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getRes().getString(R.string.action_share));
        chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (chooser.resolveActivity(getAppRes().getPackageManager()) != null) {
            getAppRes().startActivity(chooser);
        } else {
            Toast.makeText(getAppRes(), R.string.open_link_failed, Toast.LENGTH_LONG).show();
        }
    }

    public static int getAttrPixel(int attr) {
        TypedArray typedArray = ChanApplication.con.getTheme().obtainStyledAttributes(new int[]{attr});
        int pixels = typedArray.getDimensionPixelSize(0, 0);
        typedArray.recycle();
        return pixels;
    }

    public static Drawable getAttrDrawable(int attr) {
        TypedArray typedArray = ChanApplication.con.getTheme().obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static int dp(float dp) {
        return (int) (dp * getRes().getDisplayMetrics().density);
    }

    public static Typeface getTypeface(String name) {
        if (!typefaceCache.containsKey(name)) {
            Typeface typeface = Typeface.createFromAsset(getRes().getAssets(), "font/" + name);
            typefaceCache.put(name, typeface);
        }
        return typefaceCache.get(name);
    }

    /**
     * Causes the runnable to be added to the message queue. The runnable will
     * be run on the ui thread.
     *
     * @param runnable
     */
    public static void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    public static void requestKeyboardFocus(Dialog dialog, final View view) {
        view.requestFocus();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService( Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(view, 0);
            }
        });
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static String getReadableFileSize(int bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static CharSequence ellipsize(CharSequence text, int max) {
        if (text.length() <= max) {
            return text;
        } else {
            return text.subSequence(0, max) + "\u2026";
        }
    }

    public interface OnMeasuredCallback {
        void onMeasured(View view);
    }

    /**
     * Waits for a measure. Calls callback immediately if the view width and height are more than 0.
     * Otherwise it registers an onpredrawlistener and rechedules a layout.
     * Warning: the view you give must be attached to the view root!!!
     */
    public static void waitForMeasure(final View view, final OnMeasuredCallback callback) {
        waitForMeasure(true, view, callback);
    }

    public static void waitForLayout(final View view, final OnMeasuredCallback callback) {
        waitForMeasure(false, view, callback);
    }

    private static void waitForMeasure(boolean returnIfNotZero, final View view, final OnMeasuredCallback callback) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (returnIfNotZero && width > 0 && height > 0) {
            callback.onMeasured(view);
        } else {
            view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    final ViewTreeObserver observer = view.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }

                    try {
                        callback.onMeasured(view);
                    } catch (Exception e) {
                        Log.i("AndroidUtils", "Exception in onMeasured", e);
                    }

                    return true;
                }
            });
        }
    }

    public static void setPressedDrawable(View view) {
        TypedArray arr = view.getContext().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});

        Drawable drawable = arr.getDrawable(0);

        arr.recycle();
        view.setBackgroundDrawable(drawable);
    }

    public static List<View> findViewsById(ViewGroup root, int id) {
        List<View> views = new ArrayList<>();
        int childCount = root.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = root.getChildAt(i);
            if (child instanceof ViewGroup) {
                views.addAll(findViewsById((ViewGroup) child, id));
            }

            if (child.getId() == id) {
                views.add(child);
            }
        }

        return views;
    }

}
