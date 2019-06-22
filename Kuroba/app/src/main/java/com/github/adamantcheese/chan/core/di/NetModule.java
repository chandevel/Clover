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

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.net.ProxiedHurlStack;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;

import org.codejargon.feather.Provides;

import java.io.File;

import javax.inject.Singleton;

import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class NetModule {
    private static final int VOLLEY_CACHE_SIZE = 10 * 1024 * 1024;
    public static final String USER_AGENT = "Kuroba/" + BuildConfig.VERSION_NAME;

    @Provides
    @Singleton
    public RequestQueue provideRequestQueue() {
        File cacheDir = getCacheDir();
        return Volley.newRequestQueue(getAppContext(),
                USER_AGENT,
                new ProxiedHurlStack(USER_AGENT),
                new File(cacheDir, Volley.DEFAULT_CACHE_DIR), VOLLEY_CACHE_SIZE);
    }

    @Provides
    @Singleton
    public FileCache provideFileCache() {
        return new FileCache(new File(getCacheDir(), "filecache"));
    }

    private File getCacheDir() {
        // See also res/xml/filepaths.xml for the fileprovider.
        if (getAppContext().getExternalCacheDir() != null) {
            return getAppContext().getExternalCacheDir();
        } else {
            return getAppContext().getCacheDir();
        }
    }

    @Provides
    @Singleton
    public HttpCallManager provideHttpCallManager() {
        return new HttpCallManager();
    }

    @Provides
    @Singleton
    public OkHttpClient provideBasicOkHttpClient() {
        return new OkHttpClient();
    }
}
