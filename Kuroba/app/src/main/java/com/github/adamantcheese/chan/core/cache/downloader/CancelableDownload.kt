package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.FileCacheDataSource
import com.github.adamantcheese.chan.core.cache.FileCacheListener
import com.github.adamantcheese.chan.core.cache.WebmStreamingSource
import com.github.adamantcheese.chan.utils.Logger
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CancelableDownload(
        val url: String,
        private val requestCancellationThread: ExecutorService,
        val isPartOfBatchDownload: AtomicBoolean = AtomicBoolean(false)
) {
    private val state: AtomicReference<DownloadState> = AtomicReference(DownloadState.Running)
    private val callbacks: MutableSet<FileCacheListener> = mutableSetOf()
    /**
     * This callbacks are used to cancel a lot of things like the HEAD request, the get response
     * body request and response body read loop.
     * */
    private val disposeFuncList: MutableList<() -> Unit> = mutableListOf()

    fun isRunning(): Boolean = state.get() == DownloadState.Running
    fun getState(): DownloadState = state.get()

    @Synchronized
    fun addCallback(callback: FileCacheListener) {
        if (state.get() != DownloadState.Running) {
            return
        }

        callbacks.add(callback)
    }

    @Synchronized
    fun forEachCallback(func: FileCacheListener.() -> Unit) {
        callbacks.forEach { func(it) }
    }

    @Synchronized
    fun clearCallbacks() {
        callbacks.clear()
    }

    @Synchronized
    fun addDisposeFuncList(disposeFunc: () -> Unit) {
        disposeFuncList += disposeFunc
    }

    /**
     * Use this to cancel prefetches. You can't cancel them via the regular cancel() method
     * to avoid canceling prefetches when swiping through the images in the album viewer.
     * */
    fun cancelPrefetch() {
        cancel(true)
    }

    /**
     * A regular [cancel] method that cancels active downloads but not prefetch downloads.
     * */
    fun cancel() {
        cancel(false)
    }

    /**
     * Similar to [cancel] but does not delete the output file. Used by [WebmStreamingSource]
     * to stop the download without deleting the output which is then getting transferred into
     * [FileCacheDataSource]
     * */
    fun stop() {
        if (!state.compareAndSet(DownloadState.Running, DownloadState.Stopped)) {
            // Already canceled or stopped
            return
        }

        // TODO(FileCacheV2): wtf do I do in case of this file/image being prefetched?

        dispose()
    }

    private fun cancel(canCancelBatchDownloads: Boolean) {
        if (!state.compareAndSet(DownloadState.Running, DownloadState.Canceled)) {
            // Already canceled or stopped
            return
        }

        if (isPartOfBatchDownload.get() && !canCancelBatchDownloads) {
            // When prefetching media in a thread and viewing images in the same thread at the
            // same time we may accidentally cancel a prefetch download which we don't want.
            // We only want to cancel prefetch downloads when exiting a thread not when swiping
            // through the images in the album viewer.
            return
        }

        dispose()
    }

    private fun dispose() {
        // We need to cancel the network requests on a background thread.
        // We also want it to be blocking.

        requestCancellationThread.submit {
            // This may deadlock, be careful (I haven't encountered a deadlock here yet, but
            //  it's just a theoretical posibility)

            synchronized(this) {
                // Cancel downloads
                disposeFuncList.forEach { func -> func.invoke() }
                disposeFuncList.clear()
            }

            Logger.d(TAG, "Cancelling file download request, url = $url")
        }.get(MAX_CANCELLATION_WAIT_TIME_SECONDS, TimeUnit.SECONDS)
    }

    companion object {
        private const val TAG = "CancelableDownload"
        private const val MAX_CANCELLATION_WAIT_TIME_SECONDS = 5L
    }
}