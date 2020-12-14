package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import io.reactivex.Flowable
import okhttp3.HttpUrl

internal abstract class FileDownloader(
        protected val activeDownloads: ActiveDownloads,
        protected val cacheHandler: CacheHandler
) {
    abstract fun download(
            partialContentCheckResult: PartialContentCheckResult,
            url: HttpUrl,
            chunked: Boolean
    ): Flowable<FileDownloadResult>

    protected fun isRequestStoppedOrCanceled(url: HttpUrl): Boolean {
        val request = activeDownloads.get(url) ?: return true
        return !request.cancelableDownload.isRunning()
    }
}