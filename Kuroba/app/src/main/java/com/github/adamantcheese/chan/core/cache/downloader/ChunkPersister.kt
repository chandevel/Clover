package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import okhttp3.HttpUrl
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

internal class ChunkPersister(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val activeDownloads: ActiveDownloads
) {
    fun storeChunkInFile(
            url: HttpUrl,
            chunkResponse: ChunkResponse,
            totalDownloaded: AtomicLong,
            chunkIndex: Int,
            totalChunksCount: Int
    ): Flowable<ChunkDownloadEvent> {
        return Flowable.create({ emitter ->
            BackgroundUtils.ensureBackgroundThread()

            val serializedEmitter = emitter.serialize()
            val chunk = chunkResponse.chunk

            try {
                Logger.vd(this,
                        "storeChunkInFile($chunkIndex) (${maskImageUrl(url)}) " +
                                "called for chunk ${chunk.start}..${chunk.end}"
                )

                if (chunk.isWholeFile() && totalChunksCount > 1) {
                    throw IllegalStateException("storeChunkInFile($chunkIndex) Bad amount of chunks, " +
                            "should be only one but actual = $totalChunksCount")
                }

                if (!chunkResponse.response.isSuccessful) {
                    if (chunkResponse.response.code == 404) {
                        throw FileCacheException.FileNotFoundOnTheServerException()
                    }

                    throw FileCacheException.HttpCodeException(chunkResponse.response.code)
                }

                val chunkCacheFile = cacheHandler.getOrCreateChunkCacheFile(
                        chunk.start,
                        chunk.end,
                        url
                )
                        ?: throw IOException("Couldn't create chunk cache file")

                chunkResponse.response.useAsResponseBody { responseBody ->
                    val chunkSize = responseBody.contentLength()
                    if (totalChunksCount == 1) {
                        // When downloading the whole file in a single chunk we can only know
                        // for sure the whole size of the file at this point since we probably
                        // didn't send the HEAD request
                        activeDownloads.updateTotalLength(url, chunkSize)
                    }

                    responseBody.source().use { bufferedSource ->
                        if (!bufferedSource.isOpen) {
                            activeDownloads.throwCancellationException(url)
                        }

                        chunkCacheFile.useAsBufferedSink { bufferedSink ->
                            readBodyLoop(
                                    chunkSize,
                                    url,
                                    bufferedSource,
                                    bufferedSink,
                                    totalDownloaded,
                                    serializedEmitter,
                                    chunkIndex,
                                    chunkCacheFile,
                                    chunk
                            )
                        }
                    }
                }

                Logger.vd(this, "storeChunkInFile(${chunkIndex}) success, url = ${maskImageUrl(url)}, " +
                        "chunk ${chunk.start}..${chunk.end}")
            } catch (error: Throwable) {
                handleErrors(
                        url,
                        totalChunksCount,
                        error,
                        chunkIndex,
                        chunk,
                        serializedEmitter
                )
            }
        }, BackpressureStrategy.BUFFER)
    }

    @Synchronized
    private fun handleErrors(
            url: HttpUrl,
            totalChunksCount: Int,
            error: Throwable,
            chunkIndex: Int,
            chunk: Chunk,
            serializedEmitter: FlowableEmitter<ChunkDownloadEvent>
    ) {
        val state = activeDownloads.getState(url)
        val isStoppedOrCanceled = state == DownloadState.Canceled || state == DownloadState.Stopped

        // If totalChunksCount == 1 then there is nothing else to stop so we can just emit
        // one error
        if (isStoppedOrCanceled || totalChunksCount > 1 && error !is IOException) {
            Logger.vd(this, "handleErrors($chunkIndex) (${maskImageUrl(url)}) cancel for chunk ${chunk.start}..${chunk.end}")

            // First emit an error
            if (isStoppedOrCanceled) {
                // If already canceled or stopped we don't want to emit another error because
                // when emitting more than one error concurrently they will be converted into
                // a CompositeException which is a set of exceptions and it's a pain in the
                // ass to deal with.
                serializedEmitter.onComplete()
            } else {
                serializedEmitter.tryOnError(error)
            }

            // Only after that do the cancellation because otherwise we will always end up with
            // CancellationException (because almost all dispose callbacks throw it) which is not
            // an indicator of what had originally happened
            when (state) {
                DownloadState.Running,
                DownloadState.Canceled -> activeDownloads.get(url)?.cancelableDownload?.cancel()
                DownloadState.Stopped -> activeDownloads.get(url)?.cancelableDownload?.stop()
            }.exhaustive
        } else {
            Logger.vd(this, "handleErrors($chunkIndex) (${maskImageUrl(url)}) fail for chunk ${chunk.start}..${chunk.end}")
            serializedEmitter.tryOnError(error)
        }
    }

    private fun Response.useAsResponseBody(func: (ResponseBody) -> Unit) {
        this.use { response ->
            response.body?.use { responseBody ->
                func(responseBody)
            } ?: throw IOException("ResponseBody is null")
        }
    }

    private fun RawFile.useAsBufferedSink(func: (BufferedSink) -> Unit) {
        val outputStream = fileManager.getOutputStream(this)
        if (outputStream == null) {
            val fileExists = fileManager.exists(this)
            val isFile = fileManager.exists(this)
            val canWrite = fileManager.exists(this)

            throw FileCacheException.CouldNotGetOutputStreamException(
                    this.getFullPath(),
                    fileExists,
                    isFile,
                    canWrite
            )
        }

        outputStream.sink().use { sink ->
            sink.buffer().use { bufferedSink ->
                func(bufferedSink)
            }
        }
    }

    private fun readBodyLoop(
            chunkSize: Long,
            url: HttpUrl,
            bufferedSource: BufferedSource,
            bufferedSink: BufferedSink,
            totalDownloaded: AtomicLong,
            serializedEmitter: FlowableEmitter<ChunkDownloadEvent>,
            chunkIndex: Int,
            chunkCacheFile: RawFile,
            chunk: Chunk
    ) {
        var downloaded = 0L
        var notifyTotal = 0L

        val notifySize = if (chunkSize <= 0) {
            8192L
        } else {
            chunkSize / 100 // 1% increments
        }

        try {
            while (!bufferedSource.exhausted()) {
                if (isRequestStoppedOrCanceled(url)) {
                    activeDownloads.throwCancellationException(url)
                }

                val read: Long = bufferedSource.buffer.size
                downloaded += read
                bufferedSink.write(bufferedSource.buffer, bufferedSource.buffer.size)

                val total = totalDownloaded.addAndGet(read)
                activeDownloads.updateDownloaded(url, chunkIndex, total)

                if (downloaded >= notifyTotal + notifySize) {
                    notifyTotal = downloaded

                    serializedEmitter.onNext(
                            ChunkDownloadEvent.Progress(
                                    chunkIndex,
                                    downloaded,
                                    chunkSize
                            )
                    )
                }
            }

            bufferedSink.flush()

            // So that we have 100% progress for every chunk
            if (chunkSize >= 0) {
                serializedEmitter.onNext(
                        ChunkDownloadEvent.Progress(
                                chunkIndex,
                                chunkSize,
                                chunkSize
                        )
                )

                if (downloaded != chunkSize) {
                    Logger.e(this, "downloaded (${downloaded}) != chunkSize (${chunkSize})")
                    activeDownloads.throwCancellationException(url)
                }
            }

            Logger.vd(this,
                    "pipeChunk($chunkIndex) (${maskImageUrl(url)}) SUCCESS for chunk " +
                            "${chunk.start}..${chunk.end}"
            )

            serializedEmitter.onNext(
                    ChunkDownloadEvent.ChunkSuccess(
                            chunkIndex,
                            chunkCacheFile,
                            chunk
                    )
            )
            serializedEmitter.onComplete()
        } catch (error: Throwable) {
            if (DownloaderUtils.isCancellationError(error)) {
                activeDownloads.throwCancellationException(url)
            } else {
                throw error
            }
        }
    }

    private fun isRequestStoppedOrCanceled(url: HttpUrl): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: return true

        return !request.cancelableDownload.isRunning()
    }
}