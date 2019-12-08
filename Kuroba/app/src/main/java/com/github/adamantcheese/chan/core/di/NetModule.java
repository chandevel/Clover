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
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.net.ProxiedHurlStack;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.HttpCallManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import org.codejargon.feather.Provides;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class NetModule {
    public static final String USER_AGENT = getApplicationLabel() + "/" + BuildConfig.VERSION_NAME;
    public static final long DOWNLOADER_OKHTTP_TIMEOUT_SECONDS = 20L;
    public static final long PROXIED_OKHTTP_TIMEOUT_SECONDS = 30L;
    public static final long THREAD_SAVE_MANAGER_OKHTTP_TIMEOUT_SECONDS = 30L;
    public static final String THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME = "thread_save_manager_okhttp_client";
    public static final String DOWNLOADER_OKHTTP_CLIENT_NAME = "downloader_okhttp_client";
    private static final String FILE_CACHE_DIR = "filecache";

    @Provides
    @Singleton
    public RequestQueue provideRequestQueue() {
        Logger.d(AppModule.DI_TAG, "Request queue");
        return Volley.newRequestQueue(getAppContext(), new ProxiedHurlStack());
    }

    @Provides
    @Singleton
    public CacheHandler provideCacheHandler(
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Cache handler");

        RawFile cacheDirFile = fileManager.fromRawFile(new File(getCacheDir(), FILE_CACHE_DIR));
        return new CacheHandler(fileManager, cacheDirFile);
    }

    @Provides
    @Singleton
    public FileCacheV2 provideFileCacheV2(
            FileManager fileManager,
            CacheHandler cacheHandler,
            @Named(DOWNLOADER_OKHTTP_CLIENT_NAME) OkHttpClient okHttpClient
    ) {
        Logger.d(AppModule.DI_TAG, "File cache V2");
        return new FileCacheV2(fileManager, cacheHandler, okHttpClient);
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
    public HttpCallManager provideHttpCallManager(ProxiedOkHttpClient okHttpClient) {
        Logger.d(AppModule.DI_TAG, "Http call manager");
        return new HttpCallManager(okHttpClient);
    }

    // TODO: make this @Named as well instead of using hacks
    @Provides
    @Singleton
    public ProxiedOkHttpClient provideProxiedOkHttpClient() {
        Logger.d(AppModule.DI_TAG, "ProxiedOkHTTP client");
        return new ProxiedOkHttpClient();
    }

    @Provides
    @Singleton
    @Named(DOWNLOADER_OKHTTP_CLIENT_NAME)
    public OkHttpClient provideOkHttpClient() {
        Logger.d(AppModule.DI_TAG, "DownloaderOkHttp client");
        Dispatcher dispatcher = new Dispatcher(
                createExecutorServiceForOkHttpClient(4)
        );

        return new OkHttpClient.Builder()
                .connectTimeout(DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                // This seems to help with the random OkHttpClient hangups. Maybe the same thing
                // should be used for other OkHttpClients as well.
                // https://github.com/square/okhttp/issues/3146#issuecomment-311158567
                .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .build();
    }

    @Provides
    @Singleton
    @Named(THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME)
    public OkHttpClient provideOkHttpClientForThreadSaveManager() {
        Logger.d(AppModule.DI_TAG, "ThreadSaverOkHttp client");

        Dispatcher dispatcher = new Dispatcher(
                createExecutorServiceForOkHttpClient(4)
        );

        return new OkHttpClient().newBuilder()
                .connectTimeout(THREAD_SAVE_MANAGER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(THREAD_SAVE_MANAGER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(THREAD_SAVE_MANAGER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build();
    }

    private ExecutorService createExecutorServiceForOkHttpClient(int minThreadsCount) {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        if (threadsCount < minThreadsCount) {
            threadsCount = minThreadsCount;
        }

        return Executors.newFixedThreadPool(threadsCount);
    }

    //this is basically the same as OkHttpClient, but with a singleton for a proxy instance
    public class ProxiedOkHttpClient
            extends OkHttpClient {
        private OkHttpClient proxiedClient;

        public OkHttpClient getProxiedClient() {
            if (proxiedClient == null) {
                Dispatcher dispatcher = new Dispatcher(createExecutorServiceForOkHttpClient(4));

                proxiedClient = newBuilder()
                        .proxy(ChanSettings.getProxy())
                        // Proxies are usually slow, so they have increased timeouts
                        .connectTimeout(PROXIED_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(PROXIED_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .writeTimeout(PROXIED_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .dispatcher(dispatcher)
                        .build();
            }
            return proxiedClient;
        }
    }
}
