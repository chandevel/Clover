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

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource;
import com.github.adamantcheese.chan.core.net.DnsSelector;
import com.github.adamantcheese.chan.core.net.HttpEquivRefreshInterceptor;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import org.codejargon.feather.Provides;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import javax.inject.Singleton;

import okhttp3.OkHttpClient;
import okhttp3.Request;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.IPV4_ONLY;
import static com.github.adamantcheese.chan.core.net.DnsSelector.Mode.SYSTEM;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static okhttp3.Protocol.HTTP_1_1;
import static okhttp3.Protocol.HTTP_2;

public class NetModule {
    public static final String USER_AGENT = getApplicationLabel() + "/" + BuildConfig.VERSION_NAME;
    private static final String FILE_CACHE_DIR = "filecache";
    private static final String FILE_CHUNKS_CACHE_DIR = "file_chunks_cache";

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler(FileManager fileManager) {
        Logger.d(AppModule.DI_TAG, "Cache handler");

        File cacheDir = getCacheDir();
        RawFile cacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CACHE_DIR));
        RawFile chunksCacheDirFile = fileManager.fromRawFile(new File(cacheDir, FILE_CHUNKS_CACHE_DIR));

        return new CacheHandler(fileManager, cacheDirFile, chunksCacheDirFile);
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            FileManager fileManager,
            CacheHandler cacheHandler,
            SiteResolver siteResolver,
            OkHttpClientWithUtils okHttpClient
    ) {
        Logger.d(AppModule.DI_TAG, "File cache V2");
        return new FileCacheV2(fileManager, cacheHandler, siteResolver, okHttpClient);
    }

    @Provides
    @Singleton
    public WebmStreamingSource provideWebmStreamingSource(
            FileManager fileManager, FileCacheV2 fileCacheV2, CacheHandler cacheHandler
    ) {
        Logger.d(AppModule.DI_TAG, "WebmStreamingSource");
        return new WebmStreamingSource(fileManager, fileCacheV2, cacheHandler);
    }

    @Provides
    @Singleton
    public OkHttpClientWithUtils provideProxiedOkHttpClient() {
        //@formatter:off
        Logger.d(AppModule.DI_TAG, "Proxied OkHTTP client");
        return new OkHttpClientWithUtils(new OkHttpClient.Builder()
                .protocols(ChanSettings.okHttpAllowHttp2.get()
                        ? Arrays.asList(HTTP_2, HTTP_1_1) : Collections.singletonList(HTTP_1_1))
                .dns(new DnsSelector(ChanSettings.okHttpAllowIpv6.get() ? SYSTEM : IPV4_ONLY))
                // interceptor to add the User-Agent for all requests
                .addNetworkInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request requestWithUserAgent =
                            originalRequest.newBuilder().header("User-Agent", USER_AGENT).build();
                    return chain.proceed(requestWithUserAgent);
                }));
        //@formatter:on
    }

    // Basically the same as OkHttpClient, but has an extra method for constructing a proxied client for a specific call
    public static class OkHttpClientWithUtils
            extends OkHttpClient {

        public OkHttpClientWithUtils(Builder builder) {
            super(builder);
        }

        //This adds a proxy to the base client
        public OkHttpClient getProxiedClient() {
            return newBuilder().proxy(ChanSettings.getProxy()).build();
        }
    }
}
