package com.github.adamantcheese.chan.core.cache.downloader

import android.util.Log
import com.github.adamantcheese.chan.core.di.NetModule
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TestModule {

    fun provideOkHttpClient(): OkHttpClient {
        Log.d(DI_TAG, "DownloaderOkHttp client")
        return OkHttpClient.Builder()
                .connectTimeout(NetModule.DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NetModule.DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(NetModule.DOWNLOADER_OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                // This seems to help with the random OkHttpClient hangups. Maybe the same thing
                // should be used for other OkHttpClients as well.
                // https://github.com/square/okhttp/issues/3146#issuecomment-311158567
                .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                .build()
    }

    private companion object {
        private const val DI_TAG = "TestModule DI"
    }
}