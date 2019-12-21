package com.github.adamantcheese.chan.core.cache.downloader

import android.util.LruCache
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import io.reactivex.Single
import okhttp3.*
import java.io.IOException

/**
 * This class is used to figure out whether an image or file can be downloaded from the server in
 * separate chunks concurrently using HTTP Partial-Content. For batched image downloading and
 * media prefetching immediately returns false because we can just download them normally. Chunked
 * downloading should only be used for high priority files/images like in the gallery when the user
 * is viewing them.
 * */
internal class PartialContentSupportChecker(
        private val okHttpClient: OkHttpClient,
        private val activeDownloads: ActiveDownloads
) {
    private val cachedResults = LruCache<String, PartialContentCheckResult>(1024)

    fun check(url: String): Single<PartialContentCheckResult> {
        if (activeDownloads.isBatchDownload(url)) {
            return Single.just(PartialContentCheckResult(false))
        }

        val cached = cachedResults.get(url)
        if (cached != null) {
            return Single.just(cached)
        }

        val headRequest = Request.Builder()
                .head()
                .header("User-Agent", NetModule.USER_AGENT)
                .url(url)
                .build()

        val startTime = System.currentTimeMillis()

        return Single.create<PartialContentCheckResult> { emitter ->
            val call = okHttpClient.newCall(headRequest)

            val disposeFunc = {
                if (!call.isCanceled()) {
                    log(TAG, "Disposing of HEAD request")
                    call.cancel()
                }
            }

            activeDownloads.addDisposeFunc(url, disposeFunc)

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!isCancellationError(e)) {
                        emitter.onError(e)
                    } else {
                        emitter.onError(
                                FileCacheException.CancellationException(DownloadState.Canceled, url)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val acceptsRangesValue = response.header(ACCEPT_RANGES_HEADER)
                    if (acceptsRangesValue == null) {
                        emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                        return
                    }

                    if (!acceptsRangesValue.equals(ACCEPT_RANGES_HEADER_VALUE, true)) {
                        emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                        return
                    }

                    val contentLengthValue = response.header(CONTENT_LENGTH_HEADER)
                    if (contentLengthValue == null) {
                        emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                        return
                    }

                    val length = contentLengthValue.toLongOrNull()
                    if (length == null) {
                        emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                        return
                    }

                    if (length < FileCacheV2.MIN_CHUNK_SIZE) {
                        // Download tiny files normally, no need to chunk them
                        emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                        return
                    }

                    val cfCacheStatusHeader = response.header(CF_CACHE_STATUS_HEADER)
                    val diff = System.currentTimeMillis() - startTime

                    if (ChanSettings.verboseLogs.get()) {
                        log(TAG, "url = $url, fileSize = $length, " +
                                "cfCacheStatusHeader = $cfCacheStatusHeader, took = ${diff}ms")
                    }

                    emitter.onSuccess(
                            cache(
                                    url,
                                    PartialContentCheckResult(true, length)
                            )
                    )
                }
            })
        }
    }

    private fun cache(
            url: String,
            partialContentCheckResult: PartialContentCheckResult
    ): PartialContentCheckResult {
        cachedResults.put(url, partialContentCheckResult)
        return partialContentCheckResult
    }

    companion object {
        private const val TAG = "PartialContentSupportChecker"
        private const val ACCEPT_RANGES_HEADER = "Accept-Ranges"
        private const val CONTENT_LENGTH_HEADER = "Content-Length"
        private const val CF_CACHE_STATUS_HEADER = "CF-Cache-Status"
        private const val ACCEPT_RANGES_HEADER_VALUE = "bytes"
    }

}