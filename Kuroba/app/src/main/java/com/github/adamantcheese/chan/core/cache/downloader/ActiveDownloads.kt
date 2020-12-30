package com.github.adamantcheese.chan.core.cache.downloader

import androidx.annotation.GuardedBy
import okhttp3.HttpUrl

/**
 * ThreadSafe
 * */
internal open class ActiveDownloads {

    @GuardedBy("itself")
    private val activeDownloads = hashMapOf<HttpUrl, FileDownloadRequest>()

    fun clear() {
        synchronized(activeDownloads) {
            activeDownloads.values.forEach { download ->
                download.cancelableDownload.cancel()
                download.cancelableDownload.clearCallbacks()
            }
        }
    }

    fun remove(url: HttpUrl) {
        synchronized(activeDownloads) {
            val request = activeDownloads[url]
            if (request != null) {
                request.cancelableDownload.clearCallbacks()
                activeDownloads.remove(url)
            }
        }
    }

    fun containsKey(url: HttpUrl): Boolean {
        return synchronized(activeDownloads) { activeDownloads.containsKey(url) }
    }

    fun get(url: HttpUrl): FileDownloadRequest? {
        return synchronized(activeDownloads) { activeDownloads[url] }
    }

    fun put(url: HttpUrl, fileDownloadRequest: FileDownloadRequest) {
        synchronized(activeDownloads) { activeDownloads[url] = fileDownloadRequest }
    }

    fun updateTotalLength(url: HttpUrl, contentLength: Long) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.total?.set(contentLength)
        }
    }

    /**
     * [chunkIndex] is used for tests, do not change/remove it
     * */
    open fun updateDownloaded(url: HttpUrl, chunkIndex: Int, downloaded: Long) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.downloaded?.set(downloaded)
        }
    }

    fun addDisposeFunc(url: HttpUrl, disposeFunc: () -> Unit): DownloadState {
        return synchronized(activeDownloads) {
            val state = activeDownloads[url]?.cancelableDownload?.getState()
                    ?: DownloadState.Canceled

            // If already canceled - do not add any new dispose functions and call it to immediately
            // to cancel whatever it is
            if (state !is DownloadState.Running) {
                disposeFunc.invoke()
                return@synchronized state
            }

            activeDownloads[url]
                    ?.cancelableDownload
                    ?.addDisposeFuncList(disposeFunc)

            return@synchronized DownloadState.Running
        }
    }

    fun getChunks(url: HttpUrl): Set<Chunk> {
        return synchronized(activeDownloads) { activeDownloads[url]?.chunks?.toSet() ?: emptySet() }
    }

    fun clearChunks(url: HttpUrl) {
        synchronized(activeDownloads) { activeDownloads[url]?.chunks?.clear() }
    }

    fun addChunks(url: HttpUrl, chunks: List<Chunk>) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.chunks?.addAll(chunks)
        }
    }

    /**
     * Marks current CancelableDownload as canceled and throws CancellationException to terminate
     * the reactive stream
     * */
    fun throwCancellationException(url: HttpUrl): Nothing {
        val prevState = synchronized(activeDownloads) {
            val prevState = activeDownloads[url]?.cancelableDownload?.getState()
                    ?: DownloadState.Canceled

            if (prevState == DownloadState.Running) {
                activeDownloads[url]?.cancelableDownload?.cancel()
            }

            prevState
        }

        if (prevState == DownloadState.Running || prevState == DownloadState.Canceled) {
            throw FileCacheException.CancellationException(
                    DownloadState.Canceled,
                    url
            )
        } else {
            throw FileCacheException.CancellationException(
                    DownloadState.Stopped,
                    url
            )
        }
    }

    fun getState(url: HttpUrl): DownloadState {
        return synchronized(activeDownloads) {
            activeDownloads[url]?.cancelableDownload?.getState()
                    ?: DownloadState.Canceled
        }
    }

    fun count(): Int {
        return synchronized(activeDownloads) {
            activeDownloads.size
        }
    }
}