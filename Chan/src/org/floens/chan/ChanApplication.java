package org.floens.chan;

import java.lang.reflect.Field;

import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.PinnedManager;
import org.floens.chan.core.manager.PinnedManager.PinListener;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.net.BitmapLruImageCache;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.service.WatchService;
import org.floens.chan.utils.IconCache;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.ViewConfiguration;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

public class ChanApplication extends Application implements PinListener {
    public static final boolean DEVELOPER_MODE = true;

    private static ChanApplication instance;
    private static RequestQueue volleyRequestQueue;
    private static ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static PinnedManager pinnedManager;
    private static ReplyManager replyManager;
    private static DatabaseManager databaseManager;

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

    public static PinnedManager getPinnedManager() {
        return pinnedManager;
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

        if (ChanApplication.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
        }

        IconCache.createIcons(this);

        volleyRequestQueue = Volley.newRequestQueue(this);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(1024 * 1024 * 8));

        databaseManager = new DatabaseManager(this);
        boardManager = new BoardManager(this);
        pinnedManager = new PinnedManager(this);
        pinnedManager.addPinListener(this);
        replyManager = new ReplyManager(this);

        WatchService.updateRunningState(this);
    }

    @Override
    public void onPinsChanged() {
        WatchService.updateRunningState(this);
    }
}
