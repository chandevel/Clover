package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.FileCacheListener
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingDataSource
import com.github.adamantcheese.chan.core.cache.stream.WebmStreamingSource
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import okhttp3.HttpUrl
import java.util.concurrent.atomic.AtomicReference

/**
 * ThreadSafe
 * */
class CancelableDownload(
        val url: HttpUrl
) {
    private val state: AtomicReference<DownloadState> = AtomicReference(DownloadState.Running)
    private val callbacks: MutableMap<Class<*>, FileCacheListener> = mutableMapOf()

    /**
     * These callbacks are used to cancel a lot of things, like the HEAD request, the get response
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

        if (callbacks.containsKey(callback::class.java)) {
            return
        }

        callbacks[callback::class.java] = callback
    }

    @Synchronized
    fun forEachCallback(func: FileCacheListener.() -> Unit) {
        callbacks.values.forEach { callback ->
            func(callback)
        }
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
     * Similar to [cancel] but does not delete the output file. Used by [WebmStreamingSource]
     * to stop the download without deleting the output which we will then use in
     * [WebmStreamingDataSource]
     *
     * By default, stop is called when we don't want to stop it because it will usually be called from
     * WebmStreamingSource, but we actually want to stop it when stopping a gallery download.
     * */
    fun stop() {
        if (!state.compareAndSet(DownloadState.Running, DownloadState.Stopped)) {
            // Already canceled or stopped
            return
        }

        dispose()
    }

    /**
     * A regular [cancel] method that cancels active downloads but not prefetch downloads.
     * */
    fun cancel() {
        if (!state.compareAndSet(DownloadState.Running, DownloadState.Canceled)) {
            // Already canceled or stopped
            return
        }

        dispose()
    }

    private fun dispose() {
        synchronized(this) {
            // Cancel downloads
            disposeFuncList.forEach { func ->
                try {
                    func.invoke()
                } catch (error: Throwable) {
                    Logger.e(this, "Unhandled error in dispose function, " +
                            "error = ${error.javaClass.simpleName}")
                }
            }

            disposeFuncList.clear()
        }

        val action = when (state.get()) {
            DownloadState.Running -> {
                throw RuntimeException("Expected Stopped or Canceled but got Running!")
            }
            DownloadState.Stopped -> "Stopping"
            DownloadState.Canceled -> "Cancelling"
        }

        Logger.vd(this, "$action file download request, url = ${maskImageUrl(url)}")
    }
}