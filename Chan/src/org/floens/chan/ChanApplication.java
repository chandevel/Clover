package org.floens.chan;

import org.floens.chan.manager.BoardManager;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.manager.ReplyManager;
import org.floens.chan.utils.IconCache;

import android.app.Application;
import android.os.StrictMode;

import com.android.volley.RequestQueue;
import com.android.volley.extra.BitmapLruImageCache;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.Volley;

public class ChanApplication extends Application {
    public static final boolean DEVELOPER_MODE = true;
    
    private static ChanApplication instance;
    private static RequestQueue volleyRequestQueue;
    private static ImageLoader imageLoader;
    private static BoardManager boardManager;
    private static PinnedManager pinnedManager;
    
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
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        if (ChanApplication.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build());
            
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build());
        }
        
        // startService(new Intent(this, PinnedService.class));
        
        // VolleyLog.setTag(getPackageName());
        
        IconCache.createIcons(this);
        
        volleyRequestQueue = Volley.newRequestQueue(this);
        imageLoader = new ImageLoader(volleyRequestQueue, new BitmapLruImageCache(1024 * 1024 * 8));
        
        boardManager = new BoardManager(this);
        pinnedManager = new PinnedManager(this);
        new ReplyManager(this);
    }
}





