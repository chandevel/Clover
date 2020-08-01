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
import android.app.ActivityManager;
import android.app.Application;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
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

import androidx.browser.customtabs.CustomTabsIntent;

import com.google.android.material.snackbar.Snackbar;

import androidx.core.app.ActivityManagerCompat;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import org.floens.chan.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.ui.theme.ThemeHelper.theme;

public class AndroidUtils {
    private static final String TAG = "AndroidUtils";

    private static HashMap<String, Typeface> typefaceCache = new HashMap<>();

    public static Typeface ROBOTO_MEDIUM;
    public static Typeface ROBOTO_MEDIUM_ITALIC;
    public static Typeface ROBOTO_CONDENSED_REGULAR;

    @SuppressLint("StaticFieldLeak")
    private static Application application;
    private static ConnectivityManager connectivityManager;
    private static ActivityManager activityManager;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void init(Application application) {
        if (AndroidUtils.application == null) {
            AndroidUtils.application = application;

            ROBOTO_MEDIUM = getTypeface("Roboto-Medium.ttf");
            ROBOTO_MEDIUM_ITALIC = getTypeface("Roboto-MediumItalic.ttf");
            ROBOTO_CONDENSED_REGULAR = getTypeface("RobotoCondensed-Regular.ttf");

            connectivityManager = (ConnectivityManager)
                    application.getSystemService(Context.CONNECTIVITY_SERVICE);
            activityManager = (ActivityManager)
                    application.getSystemService(Context.ACTIVITY_SERVICE);
        }
    }

    public static Resources getRes() {
        return application.getResources();
    }

    public static Context getAppContext() {
        return application;
    }

    public static String getString(int res) {
        return getRes().getString(res);
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static SharedPreferences getPreferences(Application application) {
        return PreferenceManager.getDefaultSharedPreferences(application);
    }

    public static SharedPreferences getPreferences(String name) {
        return application.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * Tries to open an app that can open the specified URL.<br>
     * If this app will open the link then show a chooser to the user without this app.<br>
     * Else allow the default logic to run with startActivity.
     *
     * @param link url to open
     */
    public static void openLink(String link) {
        PackageManager pm = getAppContext().getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));

        ComponentName resolvedActivity = intent.resolveActivity(pm);
        if (resolvedActivity == null) {
            openIntentFailed();
        } else {
            boolean thisAppIsDefault = resolvedActivity.getPackageName().equals(getAppContext().getPackageName());
            if (!thisAppIsDefault) {
                openIntent(intent);
            } else {
                // Get all intents that match, and filter out this app
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
                List<Intent> filteredIntents = new ArrayList<>(resolveInfos.size());
                for (ResolveInfo info : resolveInfos) {
                    if (!info.activityInfo.packageName.equals(getAppContext().getPackageName())) {
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
    }

    public static void openLinkInBrowser(Activity activity, String link) {
        // Hack that's sort of the same as openLink
        // The link won't be opened in a custom tab if this app is the default handler for that link.
        // Manually check if this app opens it instead of a custom tab, and use the logic of
        // openLink to avoid that and show a chooser instead.
        boolean openWithCustomTabs = true;
        Intent urlIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        PackageManager pm = getAppContext().getPackageManager();
        ComponentName resolvedActivity = urlIntent.resolveActivity(pm);
        if (resolvedActivity != null) {
            openWithCustomTabs = !resolvedActivity.getPackageName().equals(getAppContext().getPackageName());
        }

        if (openWithCustomTabs) {
            CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder()
                    .setToolbarColor(theme().primaryColor.color)
                    .build();
            try {
                tabsIntent.launchUrl(activity, Uri.parse(link));
            } catch (ActivityNotFoundException e) {
                // Can't check it beforehand so catch the exception
                openIntentFailed();
            }
        } else {
            openLink(link);
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
        if (intent.resolveActivity(getAppContext().getPackageManager()) != null) {
            getAppContext().startActivity(intent);
        } else {
            openIntentFailed();
        }
    }

    private static void openIntentFailed() {
        Toast.makeText(getAppContext(), R.string.open_link_failed, Toast.LENGTH_LONG).show();
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

    public static boolean isTablet(Context context) {
        return context.getResources().getBoolean(R.bool.is_tablet);
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
        mainHandler.post(runnable);
    }

    public static void runOnUiThread(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
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
        InputMethodManager inputManager = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public static void requestViewAndKeyboardFocus(View view) {
        view.setFocusable(false);
        view.setFocusableInTouchMode(true);
        if (view.requestFocus()) {
            InputMethodManager inputManager =
                    (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
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
        /**
         * Called when the layout is done.
         *
         * @param view same view as the argument.
         * @return true to continue with rendering, false to cancel and redo the layout.
         */
        boolean onMeasured(View view);
    }

    /**
     * Waits for a measure. Calls callback immediately if the view width and height are more than 0.
     * Otherwise it registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForMeasure(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // If you call getViewTreeObserver on a view when it's not attached to a window will result in the creation of a temporarily viewtreeobserver.
            // This is almost always not what you want.
            throw new IllegalArgumentException("The view given to waitForMeasure is not attached to the window and does not have a ViewTreeObserver.");
        }

        waitForLayoutInternal(true, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener.
     * <b>Warning: the view you give must be attached to the view root!</b>
     */
    public static void waitForLayout(final View view, final OnMeasuredCallback callback) {
        if (view.getWindowToken() == null) {
            // See comment above
            throw new IllegalArgumentException("The view given to waitForLayout is not attached to the window and does not have a ViewTreeObserver.");
        }

        waitForLayoutInternal(false, view.getViewTreeObserver(), view, callback);
    }

    /**
     * Always registers an onpredrawlistener. The given ViewTreeObserver will be used.
     */
    public static void waitForLayout(final ViewTreeObserver viewTreeObserver, final View view, final OnMeasuredCallback callback) {
        waitForLayoutInternal(false, viewTreeObserver, view, callback);
    }

    private static void waitForLayoutInternal(boolean returnIfNotZero, final ViewTreeObserver viewTreeObserver, final View view, final OnMeasuredCallback callback) {
        int width = view.getWidth();
        int height = view.getHeight();

        if (returnIfNotZero && width > 0 && height > 0) {
            callback.onMeasured(view);
        } else {
            viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver usingViewTreeObserver = viewTreeObserver;
                    if (viewTreeObserver != view.getViewTreeObserver()) {
                        Logger.e(TAG, "view.getViewTreeObserver() is another viewtreeobserver! replacing with the new one");
                        usingViewTreeObserver = view.getViewTreeObserver();
                    }

                    if (usingViewTreeObserver.isAlive()) {
                        usingViewTreeObserver.removeOnPreDrawListener(this);
                    } else {
                        Logger.e(TAG, "ViewTreeObserver not alive, could not remove onPreDrawListener! This will probably not end well");
                    }

                    boolean ret;
                    try {
                        ret = callback.onMeasured(view);
                    } catch (Exception e) {
                        Logger.i(TAG, "Exception in onMeasured", e);
                        throw e;
                    }

                    if (!ret) {
                        Logger.d(TAG, "waitForLayout requested a re-layout by returning false");
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
        if (view.getParent() instanceof ViewGroup && ((ViewGroup) view.getParent()).indexOfChild(view) >= 0) {
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

    public static boolean enableHighEndAnimations() {
        boolean lowRamDevice = ActivityManagerCompat.isLowRamDevice(activityManager);
        return !lowRamDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }
}
