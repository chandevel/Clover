package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import okio.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class ChunkReader(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val activeDownloads: ActiveDownloads,
        private val verboseLogs: Boolean
) {

    fun readChunk(
            url: String,
            chunkResponse: ChunkResponse,
            totalDownloaded: AtomicLong,
            chunkIndex: Int,
            totalChunksCount: Int,
            canceled: AtomicBoolean
    ): Flowable<ChunkDownloadEvent> {
        return Flowable.create<ChunkDownloadEvent>({ emitter ->
            BackgroundUtils.ensureBackgroundThread()
            val chunk = chunkResponse.chunk

            try {
                if (verboseLogs) {
                    log(TAG,
                            "pipeChunk($chunkIndex) ($url) called for chunk ${chunk.start}..${chunk.end}"
                    )
                }

                if (chunk.isWholeFile() && totalChunksCount > 1) {
                    throw IllegalStateException("pipeChunk($chunkIndex) Bad amount of chunks, " +
                            "should be only one but actual = $totalChunksCount")
                }

                if (!chunkResponse.response.isSuccessful) {
                    if (chunkResponse.response.code == NOT_FOUND_STATUS_CODE) {
                        // TODO: probably should throw the FileNotFoundOnTheServer exception here
                        activeDownloads.throwCancellationException(url)
                    }

                    throw FileCacheException.HttpCodeException(chunkResponse.response.code)
                }

                val chunkCacheFile = cacheHandler.getOrCreateChunkCacheFile(
                        chunk.start,
                        chunk.end,
                        url
                )

                if (chunkCacheFile == null) {
                    throw IOException("Couldn't create chunk cache file")
                }

                try {
                    chunkResponse.response.useAsResponseBody { responseBody ->
                        val chunkSize = responseBody.contentLength()
                        if (chunkSize <= 0L) {
                            throw IOException("Unknown response body size, chunkSize = $chunkSize")
                        }

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
                                        canceled,
                                        url,
                                        bufferedSource,
                                        bufferedSink,
                                        totalDownloaded,
                                        emitter,
                                        chunkIndex,
                                        chunkCacheFile,
                                        chunk
                                )
                            }
                        }
                    }
                } catch (error: Throwable) {
                    deleteChunkFile(chunkCacheFile)
                    throw error
                }
            } catch (error: Throwable) {
                val isStoppedOrCanceled = activeDownloads.get(url)
                        ?.cancelableDownload
                        ?.isRunning() != true

                // TODO: if canceled is true, instead of emitting another error emit onComplete
                //  event instead
                if (isStoppedOrCanceled || totalChunksCount > 1 && error !is IOException) {
                    val state = activeDownloads.getState(url)
                    canceled.set(true)

                    when (state) {
                        DownloadState.Canceled -> {
                            activeDownloads.get(url)?.cancelableDownload?.cancel()
                        }
                        DownloadState.Stopped -> {
                            activeDownloads.get(url)?.cancelableDownload?.stop()
                        }
                        else -> {
                            throw RuntimeException("Expected: Canceled or Stopped, but " +
                                    "actual state is Running")
                        }
                    }

                    log(TAG, "pipeChunk($chunkIndex) ($url) cancel" +
                            " for chunk ${chunk.start}..${chunk.end}")
                    emitter.tryOnError(FileCacheException.CancellationException(state, url))
                    return@create
                }

                emitter.tryOnError(error)
                log(TAG, "pipeChunk($chunkIndex) ($url) fail " +
                        "for chunk ${chunk.start}..${chunk.end}")
            }
        }, BackpressureStrategy.BUFFER)
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
            canceled: AtomicBoolean,
            url: String,
            bufferedSource: BufferedSource,
            bufferedSink: BufferedSink,
            totalDownloaded: AtomicLong,
            emitter: FlowableEmitter<ChunkDownloadEvent>,
            chunkIndex: Int,
            chunkCacheFile: RawFile,
            chunk: Chunk
    ) {
        var downloaded = 0L
        var notifyTotal = 0L
        val buffer = Buffer()
        val notifySize = chunkSize / 10

        try {
            if (chunkSize <= 0) {
                throw RuntimeException("chunkSize <= 0 ($chunkSize)")
            }

            while (true) {
                if (canceled.get()) {
                    activeDownloads.throwCancellationException(url)
                }

                if (isRequestStoppedOrCanceled(url)) {
                    activeDownloads.throwCancellationException(url)
                }

                val read = bufferedSource.read(buffer, FileDownloader.BUFFER_SIZE)
                if (read == -1L) {
                    break
                }

                downloaded += read
                bufferedSink.write(buffer, read)

                val total = totalDownloaded.addAndGet(read)
                activeDownloads.updateDownloaded(url, total)

                if (downloaded >= notifyTotal + notifySize) {
                    notifyTotal = downloaded

                    emitter.onNext(
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
            emitter.onNext(
                    ChunkDownloadEvent.Progress(
                            chunkIndex,
                            chunkSize,
                            chunkSize
                    )
            )

            if (downloaded != chunkSize) {
                logError(TAG, "downloaded (${downloaded}) != chunkSize (${chunkSize})")
                activeDownloads.throwCancellationException(url)
            }

            if (verboseLogs) {
                log(TAG,
                        "pipeChunk($chunkIndex) ($url) SUCCESS for chunk ${chunk.start}..${chunk.end}"
                )
            }

            emitter.onNext(
                    ChunkDownloadEvent.ChunkSuccess(
                            chunkCacheFile,
                            chunk
                    )
            )
            emitter.onComplete()
        } finally {
            buffer.closeQuietly()
        }
    }

    private fun isRequestStoppedOrCanceled(url: String): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: return true

        return !request.cancelableDownload.isRunning()
    }

    private fun deleteChunkFile(chunkFile: RawFile) {
        if (!fileManager.delete(chunkFile)) {
            logError(TAG, "Couldn't delete chunk file: ${chunkFile.getFullPath()}")
        }
    }

    companion object {
        private const val TAG = "ChunkReader"
        private const val NOT_FOUND_STATUS_CODE = 404
    }
}