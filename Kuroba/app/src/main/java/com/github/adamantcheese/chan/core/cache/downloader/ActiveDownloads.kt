package com.github.adamantcheese.chan.core.cache.downloader

import androidx.annotation.GuardedBy

/**
 * ThreadSafe
 * */
internal class ActiveDownloads {

    @GuardedBy("itself")
    private val activeDownloads = hashMapOf<String, FileDownloadRequest>()

    fun clear() {
        synchronized(activeDownloads) {
            activeDownloads.values.forEach { download ->
                download.cancelableDownload.cancel()
                download.cancelableDownload.clearCallbacks()
            }
        }
    }

    fun remove(url: String) {
        synchronized(activeDownloads) {
            val request = activeDownloads[url]
            if (request != null) {
                request.cancelableDownload.clearCallbacks()
                activeDownloads.remove(url)
            }
        }
    }

    fun isBatchDownload(url: String): Boolean {
        return synchronized(activeDownloads) {
            return@synchronized activeDownloads[url]
                    ?.cancelableDownload
                    ?.isPartOfBatchDownload
                    ?.get()
                    ?: false
        }
    }

    fun containsKey(url: String): Boolean {
        return synchronized(activeDownloads) { activeDownloads.containsKey(url) }
    }

    fun get(url: String): FileDownloadRequest? {
        return synchronized(activeDownloads) { activeDownloads[url] }
    }

    fun put(url: String, fileDownloadRequest: FileDownloadRequest) {
        synchronized(activeDownloads) { activeDownloads[url] = fileDownloadRequest }
    }

    fun updateTotalLength(url: String, contentLength: Long) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.total?.set(contentLength)
        }
    }

    fun updateDownloaded(url: String, downloaded: Long) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.downloaded?.set(downloaded)
        }
    }

    fun addDisposeFunc(url: String, disposeFunc: () -> Unit): DownloadState {
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

            return@synchronized state
        }
    }

    fun getChunks(url: String): Set<Chunk> {
        return synchronized(activeDownloads) { activeDownloads[url]?.chunks?.toSet() ?: emptySet() }
    }

    fun addChunks(url: String, chunks: List<Chunk>) {
        synchronized(activeDownloads) {
            activeDownloads[url]?.chunks?.addAll(chunks)
        }
    }

    /**
     * Use only in tests!
     * */
    fun getAll(): List<FileDownloadRequest> {
        return synchronized(activeDownloads) { activeDownloads.values.toList() }
    }
}