package org.floens.chan;

import org.floens.chan.database.DatabaseManager;
import org.floens.chan.manager.BoardManager;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.manager.ReplyManager;
import org.floens.chan.service.PinnedService;
import org.floens.chan.utils.IconCache;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;

import com.android.volley.RequestQueue;
import com.android.volley.extra.BitmapLruImageCache;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

public class ChanApplication extends Application {
    public static final boolean DEVELOPER_MODE = true;

    private static ChanApplication instance;
    private static RequestQueue volleyRequestQueue;
    private static ImageLoader imageLoader;

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

    public static SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(instance);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (ChanApplication.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
        }

        IconCache.createIcons(this);

        volleyRequestQueue = Volley.newRequestQueue(this);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(1024 * 1024 * 8));

        new DatabaseManager(this);

        new BoardManager(this);
        new PinnedManager(this);
        new ReplyManager(this);

        startService(new Intent(this, PinnedService.class));
    }
}



