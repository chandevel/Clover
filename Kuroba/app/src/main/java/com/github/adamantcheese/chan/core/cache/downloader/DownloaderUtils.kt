package com.github.adamantcheese.chan.core.cache.downloader

import okhttp3.internal.http2.StreamResetException
import java.io.IOException

internal object DownloaderUtils {

    fun isCancellationError(error: Throwable): Boolean {
        if (error !is IOException) {
            return false
        }

        if (error is FileCacheException.CancellationException
                || error is StreamResetException) {
            return true
        }

        // Thrown by OkHttp when cancelling a call
        if (error.message?.contains("Canceled") == true) {
            return true
        }

        return false
    }

}