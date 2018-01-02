package org.floens.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;

import org.codejargon.feather.Provides;
import org.floens.chan.core.net.BitmapLruImageCache;

import javax.inject.Singleton;

public class AppModule {
    private Context applicationContext;
    private UserAgentProvider userAgentProvider;

    public AppModule(Context applicationContext, UserAgentProvider userAgentProvider) {
        this.applicationContext = applicationContext;
        this.userAgentProvider = userAgentProvider;
    }

    @Provides
    @Singleton
    public Context provideApplicationContext() {
        return applicationContext;
    }

    @Provides
    @Singleton
    public UserAgentProvider provideUserAgentProvider() {
        return userAgentProvider;
    }

    @Provides
    @Singleton
    public ImageLoader provideImageLoader(RequestQueue requestQueue) {
        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;
        return new ImageLoader(requestQueue, new BitmapLruImageCache(lruImageCacheSize));
    }
}
