package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okio.*
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

internal class ConcurrentChunkedFileDownloader @Inject constructor(
        private val okHttpClient: OkHttpClient,
        private val fileManager: FileManager,
        private val threadsCount: Int,
        activeDownloads: ActiveDownloads,
        cacheHandler: CacheHandler
) : FileDownloader(activeDownloads, cacheHandler) {

    init {
        require(threadsCount > 0)
        log(TAG, "threadsCount = $threadsCount")
    }

    private val workerThreadIndex = AtomicInteger(0)
    private val workerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(THREAD_NAME_FORMAT, workerThreadIndex.getAndIncrement())
                )
            }
    )

    override fun download(
            partialContentCheckResult: PartialContentCheckResult,
            url: String,
            chunked: Boolean
    ): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()
        require(partialContentCheckResult.length >= FileCacheV2.MIN_CHUNK_SIZE)

        val output = activeDownloads.get(url)
                ?.output
                ?: throwCancellationException(url)

        if (!fileManager.exists(output)) {
            return Flowable.error(IOException("Output file does not exist!"))
        }

        val chunksCount = if (chunked) {
            threadsCount
        } else {
            1
        }

        // Split the whole file size into chunks
        val chunks = chunkLong(
                partialContentCheckResult.length,
                chunksCount,
                FileCacheV2.MIN_CHUNK_SIZE
        )

        if (ChanSettings.verboseLogs.get()) {
            log(TAG, "File (${url}) was split into chunks: ${chunks}")
        }

        val startTime = System.currentTimeMillis()

        val downloadedChunks = Flowable.fromIterable(chunks)
                .subscribeOn(workerScheduler)
                .observeOn(workerScheduler)
                .flatMap { (chunkStart, chunkEnd) ->
                    return@flatMap processChunks(
                            url,
                            partialContentCheckResult,
                            chunkStart,
                            chunkEnd
                    )
                }

        val multicastEvent = downloadedChunks
                .doOnNext { event ->
                    check(
                            event is FileDownloadResult.Exception
                                    || event is FileDownloadResult.Progress.ChunkProgress
                                    || event is FileDownloadResult.Success.ChunkSuccess
                    ) {
                        "Event is neither Exception nor ChunkedProgress nor ChunkSuccess!!!"
                    }
                }
                .publish()
                // This is fucking important! Do not change this value unless you
                // want to change the amount of separate streams!!! Right now we need
                // only two.
                .autoConnect(2)

        // First separate stream.
        // We don't want to do anything with both Exception events and Progress
        // events we just want to pass them to the downstream
        val skipEvents = multicastEvent
                .filter { event ->
                    event is FileDownloadResult.Exception
                            || event is FileDownloadResult.Progress.ChunkProgress
                }

        // Second separate stream.
        val successEvents = multicastEvent
                .filter { event -> event is FileDownloadResult.Success.ChunkSuccess }
                .toList()
                .toFlowable()
                .flatMap { downloadedChunks ->
                    @Suppress("UNCHECKED_CAST")
                    return@flatMap writeChunksToCacheFile(
                            url,
                            downloadedChunks as List<FileDownloadResult.Success.ChunkSuccess>,
                            output,
                            startTime
                    )
                }

        // So why are we splitting a reactive stream in two? Because we need to do some
        // additional handling of ChunkedSuccess events but we don't want to do that
        // for Exception and ChunkedProgress events (We want to pass them downstream
        // right away).

        // Merge them back into a single stream
        return Flowable.merge(skipEvents, successEvents)
    }

    private fun processChunks(
            url: String,
            partialContentCheckResult: PartialContentCheckResult,
            chunkStart: Long,
            chunkEnd: Long
    ): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()
        val canceled = AtomicBoolean(false)

        if (isRequestStoppedOrCanceled(url)) {
            throwCancellationException(url)
        }

        activeDownloads.updateTotalLength(url, partialContentCheckResult.length)
        val totalDownloaded = AtomicLong(0L)
        val downloaderIndex = AtomicInteger(0)

        // Download each chunk separately in parallel
        return downloadChunk(url, chunkStart, chunkEnd - 1)
                .subscribeOn(workerScheduler)
                .observeOn(workerScheduler)
                .map { response -> ChunkResponse(chunkStart, chunkEnd, response) }
                .flatMap { chunkResponse ->
                    // Here is where the most fun is happening. At this point we have sent multiple
                    // requests to the server and got responses. Now we need to read the bodies of
                    // those responses each into it's own chunk file. Then, after we have read
                    // them all, we need to sort them and write all chunks into the resulting
                    // file - cache file. After that we need to do clean up: delete chunk files
                    // (we also need to delete them in case of an error)
                    return@flatMap pipeChunk(
                            url,
                            chunkResponse,
                            totalDownloaded,
                            downloaderIndex.getAndIncrement(),
                            canceled
                    )
                    // Retry on IO error mechanism. Apply it to each chunk individually
                    // instead of applying it to all chunks
                    .retry(MAX_RETRIES) { error ->
                        val retry = error !is CancellationException
                                && error is IOException

                        if (retry) {
                            log(TAG, "Retrying request with url ${url}, " +
                                    "error = ${error.javaClass.simpleName}")
                        }

                        retry
                    }
                }
    }

    private fun writeChunksToCacheFile(
            url: String,
            chunks: List<FileDownloadResult.Success.ChunkSuccess>,
            output: RawFile,
            requestStartTime: Long
    ): Flowable<FileDownloadResult> {
        return Flowable.fromCallable {
            if (ChanSettings.verboseLogs.get()) {
                log(TAG, "writeChunksToCacheFile called ($url), chunks count = ${chunks.size}")
            }

            try {
                // Must be sorted in ascending order!!!
                val sortedChunks = chunks.sortedBy { chunk -> chunk.chunkFileOffset }

                if (!fileManager.exists(output)) {
                    throw OutputFileDoesNotExist(output.getFullPath())
                }

                fileManager.getOutputStream(output)?.use { outputStream ->
                    // Iterate each chunk and write it to the output file
                    for (chunk in sortedChunks) {
                        val chunkFile = chunk.chunkFile

                        if (!fileManager.exists(chunkFile)) {
                            throw ChunkFileDoesNotExist(chunkFile.getFullPath())
                        }

                        fileManager.getInputStream(chunkFile)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        } ?: throw CouldNotGetInputStreamException(
                                chunkFile.getFullPath(),
                                true,
                                fileManager.isFile(chunkFile),
                                fileManager.canRead(chunkFile)
                        )
                    }

                    outputStream.flush()
                } ?: throw CouldNotGetOutputStreamException(
                        output.getFullPath(),
                        true,
                        fileManager.isFile(output),
                        fileManager.canRead(output)
                )
            } finally {
                // In case of success or an error we want delete all chunk files
                chunks.forEach { chunk ->
                    if (!fileManager.delete(chunk.chunkFile)) {
                        logError(TAG, "Couldn't delete chunk file: ${chunk.chunkFile.getFullPath()}")
                    }
                }
            }

            // Mark file as downloaded
            markFileAsDownloaded(url)

            val requestTime = System.currentTimeMillis() - requestStartTime
            return@fromCallable FileDownloadResult.Success.NormalSuccess(output, requestTime)
        }
    }

    private fun pipeChunk(
            url: String,
            chunkResponse: ChunkResponse,
            totalDownloaded: AtomicLong,
            downloaderIndex: Int,
            canceled: AtomicBoolean
    ): Flowable<FileDownloadResult> {
        return Flowable.create<FileDownloadResult>({ emitter ->
            BackgroundUtils.ensureBackgroundThread()

            if (ChanSettings.verboseLogs.get()) {
                log(TAG, "pipeChunk() ($url) called for chunk " +
                        "${chunkResponse.chunkStart}..${chunkResponse.chuckEnd}")
            }

            val disposed = AtomicBoolean(false)
            var cachedOutputStream: OutputStream? = null
            var cachedSink: Sink? = null
            var cachedBufferedSink: BufferedSink? = null
            var cachedResponseBody: ResponseBody? = null
            var cachedBufferedSource: BufferedSource? = null

            // A lambda function to dispose of response bodies for every downloaded chunk
            val disposeFunc = {
                if (disposed.compareAndSet(false, true)) {

                    if (ChanSettings.verboseLogs.get()) {
                        log(TAG, "Disposing of response body ($url) for chunk " +
                                "${chunkResponse.chunkStart}..${chunkResponse.chuckEnd}")
                    }

                    cachedOutputStream?.close()
                    cachedSink?.close()
                    cachedBufferedSink?.close()
                    cachedResponseBody?.close()
                    cachedBufferedSource?.close()
                }
            }

            activeDownloads.addDisposeFunc(url, disposeFunc)

            val chunkCacheFile = cacheHandler.getOrCreateChunkCacheFile(
                    chunkResponse.chunkStart,
                    chunkResponse.chuckEnd,
                    url
            )

            try {
                val responseBody = chunkResponse.response.body
                        ?.also { cachedResponseBody = it }
                        ?: throw NoResponseBodyException()

                var read: Long
                var downloaded: Long = 0
                var notifyTotal: Long = 0

                val chunkSize = chunkResponse.chuckEnd - chunkResponse.chunkStart
                val buffer = Buffer()
                val notifySize = chunkSize / 10
                val source = responseBody.source().also { cachedBufferedSource = it }

                if (!source.isOpen) {
                    throwCancellationException(url)
                }

                if (chunkCacheFile == null) {
                    throw IOException("Couldn't create chunk cache file")
                }

                val sink = fileManager.getOutputStream(chunkCacheFile)
                        ?.also { cachedOutputStream = it }
                        ?.sink()
                        ?.also { cachedSink = it }
                        ?.buffer()
                        ?.also { cachedBufferedSink = it }

                if (sink == null) {
                    val fileExists = fileManager.exists(chunkCacheFile)
                    val isFile = fileManager.exists(chunkCacheFile)
                    val canWrite = fileManager.exists(chunkCacheFile)

                    throw CouldNotGetOutputStreamException(
                            chunkCacheFile.getFullPath(),
                            fileExists,
                            isFile,
                            canWrite
                    )
                }

                while (true) {
                    if (canceled.get()) {
                        throwCancellationException(url)
                    }

                    if (isRequestStoppedOrCanceled(url)) {
                        throwCancellationException(url)
                    }

                    read = source.read(buffer, BUFFER_SIZE)
                    if (read == -1L) {
                        break
                    }

                    sink.write(buffer, read)

                    downloaded += read
                    val total = totalDownloaded.addAndGet(read)
                    activeDownloads.updateDownloaded(url, total)

                    if (total >= notifyTotal + notifySize) {
                        notifyTotal = total

                        emitter.onNext(
                                FileDownloadResult.Progress.ChunkProgress(
                                        downloaderIndex,
                                        downloaded,
                                        total
                                )
                        )
                    }
                }

                sink.flush()

                if (downloaded != chunkSize) {
                    logError(TAG, "downloaded (${downloaded}) != chunkSize (${chunkSize})")
                    throwCancellationException(url)
                }

                emitter.onNext(
                        FileDownloadResult.Success.ChunkSuccess(
                                chunkCacheFile,
                                chunkResponse.chunkStart
                        )
                )
                emitter.onComplete()
            } catch (error: Throwable) {
                canceled.set(true)
                activeDownloads.get(url)?.cancelableDownload?.cancel()

                if (chunkCacheFile != null) {
                    if (!fileManager.delete(chunkCacheFile)) {
                        logError(TAG, "Couldn't delete chunk file: ${chunkCacheFile.getFullPath()}")
                    }
                }

                emitter.onError(error)
            } finally {
                disposeFunc()
            }
        }, BackpressureStrategy.BUFFER)
    }

    /**
     * Marks current CancelableDownload as canceled and throws CancellationException
     * */
    private fun throwCancellationException(url: String): Nothing {
        activeDownloads.get(url)?.cancelableDownload?.cancel()
        throw CancellationException(getState(url), url)
    }

    private fun downloadChunk(url: String, from: Long, to: Long): Flowable<Response> {
        BackgroundUtils.ensureBackgroundThread()
        require(from < to) { "from >= to: $from..$to " }

        val request = activeDownloads.get(url)
                ?: throwCancellationException(url)

        val rangeHeader = String.format(RANGE_HEADER_VALUE_FORMAT, from, to)

        if (ChanSettings.verboseLogs.get()) {
            log(TAG, "Starting downloading ($url) chunk ${from}..${to}")
        }

        val httpRequest = Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT)
                .header(RANGE_HEADER, rangeHeader)
                .build()

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
                                    "manual canceling ($rangeHeader)"
                    )

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
                                CancellationException(getState(url), url)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        log(TAG, "Chunk downloaded ($url) ${from}..${to} in ${diff}ms")
                    }

                    emitter.onNext(response)
                    emitter.onComplete()
                }
            })
        }, BackpressureStrategy.BUFFER)
    }

    private data class ChunkResponse(
            val chunkStart: Long,
            val chuckEnd: Long,
            val response: Response
    )

    companion object {
        private const val TAG = "ConcurrentChunkedFileDownloader"
        private const val THREAD_NAME_FORMAT = "ConcurrentChunkedFileDownloaderThread-%d"
        private const val RANGE_HEADER = "Range"
        private const val RANGE_HEADER_VALUE_FORMAT = "bytes=%d-%d"
    }
}