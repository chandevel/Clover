package com.github.adamantcheese.chan.core.cache.downloader

import okhttp3.internal.http2.StreamResetException
import java.io.IOException

internal object DownloaderUtils {

    fun cancellationExceptionToDownloadResult(
            error: CancellationException
    ): FileDownloadResult {
        return when (error.state) {
            DownloadState.Running -> {
                throw IllegalStateException("state is running")
            }
            DownloadState.Stopped -> {
                FileDownloadResult.Stopped
            }
            DownloadState.Canceled -> {
                FileDownloadResult.Canceled
            }
        }
    }

    fun isCancellationError(error: Throwable): Boolean {
        if (error !is IOException) {
            return false
        }

        if (error is CancellationException
                || error is StreamResetException) {
            return true
        }

        if (error.message?.contains("Canceled") == true) {
            return true
        }

        return false
    }

}