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
package org.floens.chan;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.ViewConfiguration;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.net.BitmapLruImageCache;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ChanApplication extends Application {
    private static final String TAG = "ChanApplication";

    private static ChanApplication instance;
    private static RequestQueue volleyRequestQueue;
    private static ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static WatchManager watchManager;
    private static ReplyManager replyManager;
    private static DatabaseManager databaseManager;

    private List<ForegroundChangedListener> foregroundChangedListeners = new ArrayList<>();
    private int activityForegroundCounter = 0;

    public ChanApplication() {
        instance = this;
    }

    public static ChanApplication getInstance() {
        return instance;
    }

    public static RequestQueue getVolleyRequestQueue() {
        return volleyRequestQueue;
    }

    public static ImageLoader getImageLoader() {
        return imageLoader;
    }

    public static BoardManager getBoardManager() {
        return boardManager;
    }

    public static WatchManager getWatchManager() {
        return watchManager;
    }

    public static ReplyManager getReplyManager() {
        return replyManager;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(instance);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Force the overflow button to show, even on devices that have a
        // physical button.
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
        }

        if (ChanBuild.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }

        IconCache.createIcons(this);

        File cacheDir = new File(getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir(), Volley.DEFAULT_CACHE_DIR);
        volleyRequestQueue = Volley.newRequestQueue(this, null, cacheDir);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(1024 * 1024 * 8));

        databaseManager = new DatabaseManager(this);
        boardManager = new BoardManager();
        watchManager = new WatchManager(this);
        replyManager = new ReplyManager(this);
    }

    public void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            for (ForegroundChangedListener listener : foregroundChangedListeners) {
                listener.onForegroundChanged(getApplicationInForeground());
            }
        }
    }

    public void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf(TAG, "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            for (ForegroundChangedListener listener : foregroundChangedListeners) {
                listener.onForegroundChanged(getApplicationInForeground());
            }
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
    }

    public void addForegroundChangedListener(ForegroundChangedListener listener) {
        foregroundChangedListeners.add(listener);
    }

    public void removeForegroundChangedListener(ForegroundChangedListener listener) {
        foregroundChangedListeners.remove(listener);
    }

    public static interface ForegroundChangedListener {
        public void onForegroundChanged(boolean foreground);
    }
}
