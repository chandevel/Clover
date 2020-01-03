package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.utils.BackgroundUtils
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import okhttp3.*
import java.io.IOException

internal class ChunkDownloader(
        private val okHttpClient: OkHttpClient,
        private val activeDownloads: ActiveDownloads,
        private val verboseLogs: Boolean
) {

    fun downloadChunk(
            url: String,
            chunk: Chunk,
            totalChunksCount: Int
    ): Flowable<Response> {
        val request = activeDownloads.get(url)
                ?: activeDownloads.throwCancellationException(url)

        if (chunk.isWholeFile() && totalChunksCount > 1) {
            throw IllegalStateException("downloadChunk() Bad amount of chunks, " +
                    "should be only one but actual = $totalChunksCount")
        }

        if (verboseLogs) {
            log(TAG, "Start downloading ($url), chunk ${chunk.start}..${chunk.end}")
        }

        val builder = Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT)

        if (!chunk.isWholeFile()) {
            // If chunk.isWholeFile == true that means that either the file size is too small (
            // and there is no reason to download it in chunks) (it should be less than
            // [FileCacheV2.MIN_CHUNK_SIZE]) or that the server does not support Partial Content
            // or the user turned off chunked file downloading, or we couldn't send HEAD request
            // (it was timed out) so we should download it normally.
            val rangeHeader = String.format(RANGE_HEADER_VALUE_FORMAT, chunk.start, chunk.end)
            builder.header(RANGE_HEADER, rangeHeader)
        }

        val httpRequest = builder.build()
        val startTime = System.currentTimeMillis()

        return Flowable.create<Response>({ emitter ->
            BackgroundUtils.ensureBackgroundThread()
            val call = okHttpClient.newCall(httpRequest)

            // This function will be used to cancel a CHUNK (not the whole file) download upon
            // cancellation
            val disposeFunc = {
                BackgroundUtils.ensureBackgroundThread()

                if (!call.isCanceled()) {
                    log(
                            TAG,
                            "Disposing OkHttp Call for CHUNKED request ${request} via " +
                                    "manual canceling (${chunk.start}..${chunk.end})"
                    )

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
                        FileCacheException.CancellationException(
                                activeDownloads.getState(url),
                                url)
                )
                return@create
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val diff = System.currentTimeMillis() - startTime
                    log(TAG,
                            "Couldn't get chunk response, reason = ${e.javaClass.simpleName}" +
                                    " ($url) ${chunk.start}..${chunk.end}, time = ${diff}ms"
                    )

                    if (!isCancellationError(e)) {
                        emitter.tryOnError(e)
                    } else {
                        emitter.tryOnError(
                                FileCacheException.CancellationException(
                                        activeDownloads.getState(url),
                                        url
                                )
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (verboseLogs) {
                        val diff = System.currentTimeMillis() - startTime
                        log(TAG, "Got chunk response in ($url) ${chunk.start}..${chunk.end} in ${diff}ms")
                    }

                    emitter.onNext(response)
                    emitter.onComplete()
                }
            })
        }, BackpressureStrategy.BUFFER)
    }

    companion object {
        private const val TAG = "ChunkDownloader"
        private const val RANGE_HEADER = "Range"
        private const val RANGE_HEADER_VALUE_FORMAT = "bytes=%d-%d"
    }
}