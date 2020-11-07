package com.github.adamantcheese.chan.core.cache.downloader

import android.util.LruCache
import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import io.reactivex.Single
import io.reactivex.SingleEmitter
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * This class is used to figure out whether an image or a file can be downloaded from the server in
 * separate chunks concurrently using HTTP Partial-Content. Chunked downloading should only be used for high priority
 * files/images like in the gallery when the user is viewing them. Everything else should be downloaded in a singe chunk.
 * */
internal class PartialContentSupportChecker(
        private val okHttpClient: OkHttpClient,
        private val activeDownloads: ActiveDownloads,
        private val siteResolver: SiteResolver,
        private val maxTimeoutMs: Long
) {
    // Thread safe
    private val cachedResults = LruCache<HttpUrl, PartialContentCheckResult>(1024)

    @GuardedBy("itself")
    private val checkedChanHosts = mutableMapOf<String, Boolean>()

    fun check(url: HttpUrl): Single<PartialContentCheckResult> {
        val fileSize = activeDownloads.get(url)?.extraInfo?.fileSize ?: -1L
        if (fileSize > 0) {
            val hostAlreadyChecked = synchronized(checkedChanHosts) {
                checkedChanHosts.containsKey(url.host)
            }

            // If a host is already check (we sent HEAD request to it at least 1 time during the app
            // lifetime) we can go a fast route and  just check the cached value (whether the site)
            // supports partial content or not
            if (hostAlreadyChecked) {
                val siteSendsFileSizeInBytes = siteResolver.findSiteForUrl(url.host)
                        ?.chunkDownloaderSiteProperties
                        ?.siteSendsCorrectFileSizeInBytes
                        ?: false

                // Some sites may send file size in KBs (2ch.hk does that) so we can't use fileSize
                // that we get with json for such sites and we have to determine the file size
                // by sending HEAD requests every time
                if (siteSendsFileSizeInBytes) {
                    val supportsPartialContent = synchronized(checkedChanHosts) {
                        checkedChanHosts[url.host] ?: false
                    }

                    if (supportsPartialContent) {
                        // Fast path: we already had a file size and already checked whether this
                        // chan supports Partial Content. So we don't need to send HEAD request.
                        return Single.just(
                                PartialContentCheckResult(
                                        supportsPartialContentDownload = true,
                                        // We are not sure about this one but it doesn't matter
                                        // because we have another similar check in the downloader.
                                        notFoundOnServer = false,
                                        length = fileSize
                                )
                        )
                    } else {
                        return Single.just(
                                PartialContentCheckResult(
                                        supportsPartialContentDownload = false
                                )
                        )
                    }
                }
            }
        }

        val cached = cachedResults.get(url)
        if (cached != null) {
            return Single.just(cached)
        }

        if (ChanSettings.verboseLogs.get()) {
            Logger.d(this, "Sending HEAD request to url (${maskImageUrl(url)})")
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
                if (ChanSettings.verboseLogs.get() && !call.isCanceled()) {
                    Logger.d(this, "Disposing of HEAD request for url (${maskImageUrl(url)})")
                    call.cancel()
                }
            }

            val downloadState = activeDownloads.addDisposeFunc(url, disposeFunc)
            if (downloadState != DownloadState.Running) {
                when (downloadState) {
                    DownloadState.Canceled -> activeDownloads.get(url)?.cancelableDownload?.cancel()
                    DownloadState.Stopped -> activeDownloads.get(url)?.cancelableDownload?.stop()
                    else -> {
                        emitter.tryOnError(
                                RuntimeException("DownloadState must be either Stopped or Canceled")
                        )
                        return@create
                    }
                }

                emitter.tryOnError(
                        FileCacheException.CancellationException(downloadState, url)
                )
                return@create
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (emitter.isDisposed) {
                        return
                    }

                    if (!isCancellationError(e)) {
                        emitter.tryOnError(e)
                    } else {
                        val state = activeDownloads.get(url)?.cancelableDownload?.getState()
                                ?: DownloadState.Canceled

                        if (state == DownloadState.Running) {
                            throw RuntimeException("Expected Cancelled or Stopped but got Running")
                        }

                        emitter.tryOnError(
                                FileCacheException.CancellationException(state, url)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (emitter.isDisposed) {
                        return
                    }

                    handleResponse(response, url, emitter, startTime)
                }
            })
        }
                // Some HEAD requests on 4chan may take a lot of time (like 2 seconds or even more) when
                // a file is not cached by the cloudflare so if a request takes more than [MAX_TIMEOUT_MS]
                // we assume that cloudflare doesn't have this file cached so we just download it normally
                // without using Partial Content
                .timeout(maxTimeoutMs, TimeUnit.MILLISECONDS)
                .doOnSuccess {
                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        Logger.d(this, "HEAD request to url (${maskImageUrl(url)}) has succeeded, time = ${diff}ms")
                    }
                }
                .doOnError { error ->
                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        Logger.e(this, "HEAD request to url (${maskImageUrl(url)}) has failed " +
                                "because of \"${error.javaClass.simpleName}\" exception, time = ${diff}ms")
                    }
                }
                .onErrorResumeNext { error ->
                    if (error !is TimeoutException) {
                        return@onErrorResumeNext Single.error(error)
                    }

                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        Logger.d(this, "HEAD request took for url (${maskImageUrl(url)}) too much time, " +
                                "canceled by timeout() operator, took = ${diff}ms")
                    }

                    // Do not cache this result because after this request the file should be cached by the
                    // cloudflare, so the next time we open it, it should load way faster
                    return@onErrorResumeNext Single.just(
                            PartialContentCheckResult(
                                    supportsPartialContentDownload = false
                            )
                    )
                }
    }

    private fun handleResponse(
            response: Response,
            url: HttpUrl,
            emitter: SingleEmitter<PartialContentCheckResult>,
            startTime: Long
    ) {
        val statusCode = response.code
        if (statusCode == 404) {
            // Fast path: the server returned 404 so that mean we don't have to do any other GET
            // requests since the file does not exist
            val result = PartialContentCheckResult(
                    supportsPartialContentDownload = false,
                    notFoundOnServer = true
            )
            cache(url, result)

            emitter.tryOnError(FileCacheException.FileNotFoundOnTheServerException())
            return
        }

        val acceptsRangesValue = response.header(ACCEPT_RANGES_HEADER)
        if (acceptsRangesValue == null) {
            if (ChanSettings.verboseLogs.get()) {
                Logger.d(this, "(${maskImageUrl(url)}) does not support partial content (ACCEPT_RANGES_HEADER is null")
            }
            emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
            return
        }

        if (!acceptsRangesValue.equals(ACCEPT_RANGES_HEADER_VALUE, true)) {
            if (ChanSettings.verboseLogs.get()) {
                Logger.d(this, "(${maskImageUrl(url)}) does not support partial content " +
                        "(bad ACCEPT_RANGES_HEADER = ${acceptsRangesValue})")
            }
            emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
            return
        }

        val contentLengthValue = response.header(CONTENT_LENGTH_HEADER)
        if (contentLengthValue == null) {
            // 8kun doesn't send Content-Length header whatsoever, but it sends correct file size
            // in thread.json. So we can try using that.

            if (!canWeUseFileSizeFromJson(url)) {
                if (ChanSettings.verboseLogs.get()) {
                    Logger.d(this, "(${maskImageUrl(url)}) does not support partial content (CONTENT_LENGTH_HEADER is null")
                }
                emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
                return
            }
        }

        val length = if (contentLengthValue != null) {
            contentLengthValue.toLongOrNull()
        } else {
            activeDownloads.get(url)?.extraInfo?.fileSize ?: -1L
        }

        if (length == null || length <= 0) {
            if (ChanSettings.verboseLogs.get()) {
                Logger.d(this, "(${maskImageUrl(url)}) does not support partial content " +
                        "(bad CONTENT_LENGTH_HEADER = ${contentLengthValue})")
            }
            emitter.onSuccess(cache(url, PartialContentCheckResult(false)))
            return
        }

        if (length < FileCacheV2.MIN_CHUNK_SIZE) {
            if (ChanSettings.verboseLogs.get()) {
                Logger.d(this, "(${maskImageUrl(url)}) download file normally (file length < MIN_CHUNK_SIZE, length = $length)")
            }
            // Download tiny files normally, no need to chunk them
            emitter.onSuccess(cache(url, PartialContentCheckResult(false, length = length)))
            return
        }

        val cfCacheStatusHeader = response.header(CF_CACHE_STATUS_HEADER)
        val diff = System.currentTimeMillis() - startTime

        if (ChanSettings.verboseLogs.get()) {
            Logger.d(this, "url = ${maskImageUrl(url)}, fileSize = $length, " +
                    "cfCacheStatusHeader = $cfCacheStatusHeader, took = ${diff}ms")
        }

        synchronized(checkedChanHosts) { checkedChanHosts.put(url.host, true) }

        val result = PartialContentCheckResult(
                supportsPartialContentDownload = true,
                notFoundOnServer = false,
                length = length
        )

        emitter.onSuccess(cache(url, result))
    }

    private fun canWeUseFileSizeFromJson(url: HttpUrl): Boolean {
        val fileSize = activeDownloads.get(url)?.extraInfo?.fileSize ?: -1L
        if (fileSize <= 0) {
            return false
        }

        return siteResolver.findSiteForUrl(url.host)
                ?.chunkDownloaderSiteProperties
                ?.siteSendsCorrectFileSizeInBytes
                ?: false
    }

    private fun cache(
            url: HttpUrl,
            partialContentCheckResult: PartialContentCheckResult
    ): PartialContentCheckResult {
        cachedResults.put(url, partialContentCheckResult)
        return partialContentCheckResult
    }

    /**
     * For tests
     * */
    fun clear() {
        cachedResults.evictAll()
    }

    companion object {
        private const val ACCEPT_RANGES_HEADER = "Accept-Ranges"
        private const val CONTENT_LENGTH_HEADER = "Content-Length"
        private const val CF_CACHE_STATUS_HEADER = "CF-Cache-Status"
        private const val ACCEPT_RANGES_HEADER_VALUE = "bytes"
    }

}