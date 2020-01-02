package com.github.adamantcheese.chan.core.cache.downloader

import android.util.Log
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class TestModule {

    fun provideOkHttpClient(): OkHttpClient {
        Log.d(DI_TAG, "DownloaderOkHttp client")
        return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build()
    }

    private companion object {
        private const val DI_TAG = "TestModule DI"
    }
}