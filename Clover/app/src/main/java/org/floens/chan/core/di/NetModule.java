package org.floens.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.codejargon.feather.Provides;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.net.ProxiedHurlStack;

import java.io.File;

import javax.inject.Singleton;

public class NetModule {
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";

    @Provides
    @Singleton
    public RequestQueue provideRequestQueue(Context applicationContext, UserAgentProvider userAgentProvider) {
        File cacheDir = getCacheDir(applicationContext);

        String userAgent = userAgentProvider.getUserAgent();
        return Volley.newRequestQueue(applicationContext,
                userAgent,
                new ProxiedHurlStack(userAgent),
                new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);
    }

    @Provides
    @Singleton
    public FileCache provideFileCache(Context applicationContext, UserAgentProvider userAgentProvider) {
        return new FileCache(new File(getCacheDir(applicationContext), FILE_CACHE_NAME), userAgentProvider.getUserAgent());
    }

    private File getCacheDir(Context applicationContext) {
        // See also res/xml/filepaths.xml for the fileprovider.
        if (applicationContext.getExternalCacheDir() != null) {
            return applicationContext.getExternalCacheDir();
        } else {
            return applicationContext.getCacheDir();
        }
    }
}
