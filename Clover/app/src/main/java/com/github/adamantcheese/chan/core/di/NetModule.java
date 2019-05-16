/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

import org.codejargon.feather.Provides;
import com.adamantcheese.github.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.net.ProxiedHurlStack;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;

import java.io.File;

import javax.inject.Singleton;

public class NetModule {
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;
    private static final long FILE_CACHE_DISK_SIZE = 50 * 1024 * 1024;
    private static final String FILE_CACHE_NAME = "filecache";
    public static final String USER_AGENT = "Kuroba/" + BuildConfig.VERSION_NAME;

    @Provides
    @Singleton
    public RequestQueue provideRequestQueue(Context applicationContext) {
        File cacheDir = getCacheDir(applicationContext);
        return Volley.newRequestQueue(applicationContext,
                USER_AGENT,
                new ProxiedHurlStack(USER_AGENT),
                new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);
    }

    @Provides
    @Singleton
    public FileCache provideFileCache(Context applicationContext) {
        return new FileCache(new File(getCacheDir(applicationContext), FILE_CACHE_NAME), FILE_CACHE_DISK_SIZE, USER_AGENT);
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
    public HttpCallManager provideHttpCallManager() {
        return new HttpCallManager();
    }
}
