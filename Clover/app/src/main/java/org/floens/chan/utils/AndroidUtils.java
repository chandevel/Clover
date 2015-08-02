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

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import org.floens.chan.Chan;
import org.floens.chan.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class AndroidUtils {
    private static HashMap<String, Typeface> typefaceCache = new HashMap<>();

    public static Typeface ROBOTO_MEDIUM;
    public static Typeface ROBOTO_MEDIUM_ITALIC;

    private static ConnectivityManager connectivityManager;

    public static void init() {
        ROBOTO_MEDIUM = getTypeface("Roboto-Medium.ttf");
        ROBOTO_MEDIUM_ITALIC = getTypeface("Roboto-MediumItalic.ttf");

        connectivityManager = (ConnectivityManager) getAppRes().getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public static Resources getRes() {
        return Chan.con.getResources();
    }

    public static Context getAppRes() {
        return Chan.con;
    }

    public static String getString(int res) {
        return getRes().getString(res);
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(Chan.con);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void openWebView(Activity activity, String title, String link) {
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(R.layout.dialog_web);
        WebView wb = (WebView) dialog.findViewById(R.id.web_view);
        wb.getSettings().setJavaScriptEnabled(true);
        wb.loadUrl(link);
        dialog.setTitle(title);
        dialog.setCancelable(true);
        dialog.show();
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        PackageManager pm = getAppRes().getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        boolean thisAppIsDefault = resolvedActivity.getPackageName().equals(getAppRes().getPackageName());
        if (!thisAppIsDefault) {
            openIntent(intent);
        } else {
            // Get all intents that match, and filter out this app
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());
            for (ResolveInfo info : resolveInfos) {
                if (!info.activityInfo.packageName.equals(getAppRes().getPackageName())) {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                    i.setPackage(info.activityInfo.packageName);
                    filteredIntents.add(i);
                }
            }

            if (filteredIntents.size() > 0) {
                // Create a chooser for the last app in the list, and add the rest with EXTRA_INITIAL_INTENTS that get placed above
                Intent chooser = Intent.createChooser(filteredIntents.remove(filteredIntents.size() - 1), null);
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, filteredIntents.toArray(new Intent[filteredIntents.size()]));
                openIntent(chooser);
            } else {
                openIntentFailed();
            }
        }
    }

    public static void shareLink(String link) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, link);
        Intent chooser = Intent.createChooser(intent, getRes().getString(R.string.action_share));
        openIntent(chooser);
    }

    public static void openIntent(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getAppRes().getPackageManager()) != null) {
            getAppRes().startActivity(intent);
        } else {
            openIntentFailed();
        }
    }

    private static void openIntentFailed() {
        Toast.makeText(getAppRes(), R.string.open_link_failed, Toast.LENGTH_LONG).show();
    }

    public static int getAttrColor(Context context, int attr) {
        TypedArray typedArray = context.getTheme().obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0, 0);
        typedArray.recycle();
        return color;
    }

    public static Drawable getAttrDrawable(Context context, int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        Drawable drawable = typedArray.getDrawable(0);
        typedArray.recycle();
        return drawable;
    }

    public static int getDimen(Context context, int dimen) {
        return context.getResources().getDimensionPixelSize(dimen);
    }

    public static int dp(float dp) {
        return (int) (dp * getRes().getDisplayMetrics().density);
    }

    public static int sp(float sp) {
        return (int) (sp * getRes().getDisplayMetrics().scaledDensity);
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
     */
    public static void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }

    public static void runOnUiThread(Runnable runnable, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(runnable, delay);
    }

    public static void requestKeyboardFocus(Dialog dialog, final View view) {
        view.requestFocus();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                requestKeyboardFocus(view);
            }
        });
    }

    public static void requestKeyboardFocus(final View view) {
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static String getReadableFileSize(long bytes, boolean si) {
        long unit = si ? 1000 : 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    public static CharSequence ellipsize(CharSequence text, int max) {
        if (text.length() <= max) {
            return text;
        } else {
            return text.subSequence(0, max) + "\u2026";
        }
    }

    public interface OnMeasuredCallback {
        boolean onMeasured(View view);
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

                    boolean ret;
                    try {
                        ret = callback.onMeasured(view);
                    } catch (Exception e) {
                        Log.i("AndroidUtils", "Exception in onMeasured", e);
                        throw e;
                    }

                    return ret;
                }
            });
        }
    }

    public static void setRoundItemBackground(View view) {
        if (isLollipop()) {
            setRoundItemBackgroundLollipop(view);
        } else {
            view.setBackgroundResource(R.drawable.item_background);
        }
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

    public static boolean removeFromParentView(View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
            return true;
        } else {
            return false;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void setRoundItemBackgroundLollipop(View view) {
        view.setBackground(getAttrDrawable(view.getContext(), android.R.attr.selectableItemBackgroundBorderless));
    }

    public static boolean isLollipop() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static void setElevation(View view, float elevation) {
        if (isLollipop()) {
            setElevationLollipop(view, elevation);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static void setElevationLollipop(View view, float elevation) {
        view.setElevation(elevation);
    }

    public static void fixSnackbarText(Context context, Snackbar snackbar) {
        ((TextView) snackbar.getView().findViewById(R.id.snackbar_text)).setTextColor(0xffffffff);
        snackbar.setActionTextColor(getAttrColor(context, R.attr.colorAccent));
    }

    public static ConnectivityManager getConnectivityManager() {
        return connectivityManager;
    }

    public static boolean isConnected(int type) {
        NetworkInfo networkInfo = connectivityManager.getNetworkInfo(type);
        return networkInfo != null && networkInfo.isConnected();
    }
}
