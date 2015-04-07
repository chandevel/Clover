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
import android.content.Context;
import android.os.StrictMode;
import android.view.ViewConfiguration;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.net.BitmapLruImageCache;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.FileCache;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ChanApplication extends Application {
    private static final String TAG = "ChanApplication";

    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";
    private static final int VOLLEY_LRU_CACHE_SIZE = 8 * 1024 * 1024;
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;

    public static Context con;

    private static ChanApplication instance;
    private static RequestQueue volleyRequestQueue;
    private static com.android.volley.toolbox.ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static WatchManager watchManager;
    private static ReplyManager replyManager;
    private static DatabaseManager databaseManager;
    private static FileCache fileCache;

    private List<ForegroundChangedListener> foregroundChangedListeners = new ArrayList<>();
    private int activityForegroundCounter = 0;

    public ChanApplication() {
        instance = this;
        con = this;
    }

    public static ChanApplication getInstance() {
        return instance;
    }

    public static RequestQueue getVolleyRequestQueue() {
        return volleyRequestQueue;
    }

    public static ImageLoader getVolleyImageLoader() {
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

    public static FileCache getFileCache() {
        return fileCache;
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

        AndroidUtils.init();

        ChanUrls.loadScheme(ChanSettings.getNetworkHttps());

        IconCache.createIcons(this);

        cleanupOutdated();

        File cacheDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();

        replyManager = new ReplyManager(this);

        volleyRequestQueue = Volley.newRequestQueue(this, replyManager.getUserAgent(), null, new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(VOLLEY_LRU_CACHE_SIZE));

        fileCache = new FileCache(new File(cacheDir, FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE);

        databaseManager = new DatabaseManager(this);
        boardManager = new BoardManager();
        watchManager = new WatchManager(this);

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

    private void cleanupOutdated() {
        File ionCacheFolder = new File(getCacheDir() + "/ion");
        if (ionCacheFolder.exists() && ionCacheFolder.isDirectory()) {
            Logger.i(TAG, "Clearing old ion folder");
            for (File file : ionCacheFolder.listFiles()) {
                if (!file.delete()) {
                    Logger.i(TAG, "Could not delete old ion file " + file.getName());
                }
            }
            if (!ionCacheFolder.delete()) {
                Logger.i(TAG, "Could not delete old ion folder");
            } else {
                Logger.i(TAG, "Deleted old ion folder");
            }
        }
    }

    public interface ForegroundChangedListener {
        void onForegroundChanged(boolean foreground);
    }
}
