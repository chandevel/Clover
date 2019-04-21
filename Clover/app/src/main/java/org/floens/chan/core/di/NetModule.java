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
package org.floens.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.codejargon.feather.Provides;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.net.ProxiedHurlStack;
import org.floens.chan.core.site.http.HttpCallManager;

import java.io.File;

import javax.inject.Singleton;

public class NetModule {
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;
    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
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
        return new FileCache(new File(getCacheDir(applicationContext), FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE, userAgentProvider.getUserAgent());
    }

    private File getCacheDir(Context applicationContext) {
        // See also res/xml/filepaths.xml for the fileprovider.
        if (applicationContext.getExternalCacheDir() != null) {
            return applicationContext.getExternalCacheDir();
        } else {
            return applicationContext.getCacheDir();
        }
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager(UserAgentProvider userAgentProvider) {
        return new HttpCallManager(userAgentProvider);
    }
}
