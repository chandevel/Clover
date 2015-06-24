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
import android.content.pm.PackageManager;
import android.os.StrictMode;
import android.view.ViewConfiguration;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;
import com.squareup.leakcanary.RefWatcher;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.net.BitmapLruImageCache;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Locale;

import de.greenrobot.event.EventBus;

public class Chan extends Application {
    private static final String TAG = "ChanApplication";

    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";
    private static final int VOLLEY_LRU_CACHE_SIZE = 8 * 1024 * 1024;
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;

    public static Context con;

    private static Chan instance;
    private static RequestQueue volleyRequestQueue;
    private static com.android.volley.toolbox.ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static WatchManager watchManager;
    private static ReplyManager replyManager;
    private static DatabaseManager databaseManager;
    private static FileCache fileCache;
    private static RefWatcher refWatcher;

    private String userAgent;
    private int activityForegroundCounter = 0;

    public Chan() {
        instance = this;
        con = this;
    }

    public static Chan getInstance() {
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

    public static RefWatcher getRefWatcher() {
        return refWatcher;
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
//            refWatcher = LeakCanary.install(this);
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectCustomSlowCalls().detectNetwork().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }

        AndroidUtils.init();

        ChanUrls.loadScheme(ChanSettings.networkHttps.get());

        // User agent is <appname>/<version>
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        version = version.toLowerCase(Locale.ENGLISH).replace(" ", "_");
        userAgent = getString(R.string.app_name) + "/" + version;

        cleanupOutdated();

        File cacheDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();

        replyManager = new ReplyManager(this);

        volleyRequestQueue = Volley.newRequestQueue(this, getUserAgent(), null, new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(VOLLEY_LRU_CACHE_SIZE));

        fileCache = new FileCache(new File(cacheDir, FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE, getUserAgent());

        databaseManager = new DatabaseManager(this);
        boardManager = new BoardManager();
        watchManager = new WatchManager(this);
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf(TAG, "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
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

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }
}
