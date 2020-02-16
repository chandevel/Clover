package com.github.adamantcheese.chan.core.cache

import com.github.adamantcheese.chan.core.cache.downloader.*
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.exceptions.CompositeException

internal object ErrorMapper {
    private const val TAG = "ErrorMapper"

    fun mapError(
            url: String,
            throwable: Throwable,
            activeDownloads: ActiveDownloads
    ): FileDownloadResult? {
        // CompositeException is a RxJava type of exception that is being thrown when multiple
        // exceptions are being thrown concurrently from multiple threads (e.g. You have a reactive
        // stream that splits into multiple streams and all those streams throw an exceptions).
        // RxJava accumulates all those exceptions and stores them all in the CompositeException.
        // It's pain in the ass to deal with because you have to log them all and then figure
        // out which one of them is the most important to you to do some kind of handling.
        val error = if (throwable is CompositeException) {
            require(throwable.exceptions.size > 0) {
                "Got CompositeException without exceptions!"
            }

            if (throwable.exceptions.size == 1) {
                throwable.exceptions.first()
            } else {
                extractErrorFromCompositeException(throwable.exceptions)
            }
        } else {
            throwable
        }

        if (error is FileCacheException.CancellationException) {
            return when (error.state) {
                DownloadState.Running -> {
                    throw RuntimeException("Got cancellation exception but the state is still running!")
                }
                DownloadState.Stopped -> FileDownloadResult.Stopped
                DownloadState.Canceled -> FileDownloadResult.Canceled
            }
        }

        if (DownloaderUtils.isCancellationError(error)) {
            return when (activeDownloads.getState(url)) {
                DownloadState.Running -> {
                    throw RuntimeException("Got cancellation exception but the state is still running!")
                }
                DownloadState.Stopped -> FileDownloadResult.Stopped
                else -> FileDownloadResult.Canceled
            }
        }

        if (error is FileCacheException) {
            return FileDownloadResult.KnownException(error)
        }

        return FileDownloadResult.UnknownException(error)
    }

    private fun extractErrorFromCompositeException(exceptions: List<Throwable>): Throwable {
        val cancellationException = exceptions.firstOrNull { exception ->
            exception is FileCacheException.CancellationException
        }

        if (cancellationException != null) {
            return cancellationException
        }

        if (exceptions.all { it is FileCacheException.CancellationException }) {
            return exceptions.first()
        }

        for (exception in exceptions) {
            Logger.e(TAG, "Composite exception error: " +
                    "${exception.javaClass.name}, message: ${exception.message}")
        }

        return exceptions.first()
    }

}