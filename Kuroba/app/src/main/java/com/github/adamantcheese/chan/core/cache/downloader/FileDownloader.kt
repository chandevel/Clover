package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.utils.BackgroundUtils
import io.reactivex.Flowable

internal abstract class FileDownloader(
        protected val activeDownloads: ActiveDownloads,
        protected val cacheHandler: CacheHandler
) {
    abstract fun download(
            partialContentCheckResult: PartialContentCheckResult,
            url: String,
            chunked: Boolean
    ): Flowable<FileDownloadResult>

    protected fun isRequestStoppedOrCanceled(url: String): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: return true

        return !request.cancelableDownload.isRunning()
    }

    protected fun getState(url: String): DownloadState {
        return activeDownloads.get(url)?.cancelableDownload?.getState()
                ?: DownloadState.Canceled
    }

    protected fun markFileAsDownloaded(url: String) {
        val request = checkNotNull(activeDownloads.get(url)) {
            "Active downloads does not have url: ${url} even though " +
                    "it was just downloaded"
        }

        if (!cacheHandler.markFileDownloaded(request.output)) {
            throw FileCacheException.CouldNotMarkFileAsDownloaded(request.output)
        }
    }

    companion object {
        internal const val BUFFER_SIZE: Long = 8192L
        internal const val MAX_RETRIES = 5L
    }
}