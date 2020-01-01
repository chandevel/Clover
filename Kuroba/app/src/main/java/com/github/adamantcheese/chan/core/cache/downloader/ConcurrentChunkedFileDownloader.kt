package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.Scheduler
import okhttp3.*
import okhttp3.internal.closeQuietly
import okio.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

internal class ConcurrentChunkedFileDownloader @Inject constructor(
        private val okHttpClient: OkHttpClient,
        private val fileManager: FileManager,
        private val workerScheduler: Scheduler,
        activeDownloads: ActiveDownloads,
        cacheHandler: CacheHandler
) : FileDownloader(activeDownloads, cacheHandler) {

    override fun download(
            partialContentCheckResult: PartialContentCheckResult,
            url: String,
            chunked: Boolean
    ): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()

        val output = activeDownloads.get(url)
                ?.output
                ?: throwCancellationException(url)

        if (!fileManager.exists(output)) {
            return Flowable.error(IOException("Output file does not exist!"))
        }

        // We can't use Partial Content if we don't know the file size
        val chunksCount = if (chunked && partialContentCheckResult.couldDetermineFileSize()) {
            activeDownloads.get(url)
                    ?.chunksCount
                    ?: throwCancellationException(url)
        } else {
            1
        }

        check(chunksCount >= 1) { "Chunks count is less than 1 = $chunksCount" }

        // Split the whole file size into chunks
        val chunks = if (chunksCount > 1) {
            chunkLong(
                    partialContentCheckResult.length,
                    chunksCount,
                    FileCacheV2.MIN_CHUNK_SIZE
            )
        } else {
            // If there is only one chunk then we should download the whole file without using
            // Partial Content
            listOf(Chunk.wholeFile())
        }

        return Flowable.concat(
                Flowable.just(FileDownloadResult.Start(chunksCount)),
                downloadInternal(
                        url,
                        chunks,
                        partialContentCheckResult,
                        output
                )
                .doOnSubscribe { log(TAG, "Starting downloading (${url})") }
                .doOnComplete { log(TAG, "Completed downloading (${url})") }
                .doOnError { error ->
                    logError(TAG, "Error while trying to download (${url}) " +
                            "error name = ${error.javaClass.simpleName}")
                }
        )
    }

    private fun downloadInternal(
            url: String,
            chunks: List<Chunk>,
            partialContentCheckResult: PartialContentCheckResult,
            output: AbstractFile
    ): Flowable<FileDownloadResult> {
        if (ChanSettings.verboseLogs.get()) {
            log(TAG, "File (${url}) was split into chunks: ${chunks}")
        }

        if (!partialContentCheckResult.couldDetermineFileSize() && chunks.size != 1) {
            throw IllegalStateException("The size of the file is unknown but chunks size is not 1, " +
                    "size = ${chunks.size}, chunks = $chunks")
        }

        if (isRequestStoppedOrCanceled(url)) {
            throwCancellationException(url)
        }

        val startTime = System.currentTimeMillis()
        val canceled = AtomicBoolean(false)

        if (partialContentCheckResult.couldDetermineFileSize()) {
            activeDownloads.updateTotalLength(url, partialContentCheckResult.length)
        }

        val totalDownloaded = AtomicLong(0L)
        val chunkIndex = AtomicInteger(0)

        activeDownloads.addChunks(url, chunks)

        val downloadedChunks = Flowable.fromIterable(chunks)
                .subscribeOn(workerScheduler)
                .observeOn(workerScheduler)
                .flatMap { chunk ->
                    return@flatMap processChunks(
                            url,
                            totalDownloaded,
                            chunkIndex.getAndIncrement(),
                            canceled,
                            chunk,
                            chunks.size
                    )
                }
                .onErrorReturn { error -> ChunkDownloadEvent.ChunkError(error) }

        val multicastEvent = downloadedChunks
                .doOnNext { event ->
                    check(
                            event is ChunkDownloadEvent.Progress
                                    || event is ChunkDownloadEvent.ChunkSuccess
                                    || event is ChunkDownloadEvent.ChunkError
                    ) {
                        "Event is neither ChunkDownloadEvent.Progress " +
                                "nor ChunkDownloadEvent.ChunkSuccess " +
                                "nor ChunkDownloadEvent.ChunkError !!!"
                    }
                }
                .publish()
                // This is fucking important! Do not change this value unless you
                // want to change the amount of separate streams!!! Right now we need
                // only two.
                .autoConnect(2)

        // First separate stream.
        // We don't want to do anything with Progress events we just want to pass them
        // to the downstream
        val skipEvents = multicastEvent
                .filter { event -> event is ChunkDownloadEvent.Progress }

        // Second separate stream.
        val successEvents = multicastEvent
                .filter { event ->
                    return@filter event is ChunkDownloadEvent.ChunkSuccess
                            || event is ChunkDownloadEvent.ChunkError
                }
                .toList()
                .toFlowable()
                .flatMap { chunkEvents ->
                    if (chunkEvents.isEmpty()) {
                        throwCancellationException(url)
                    }

                    if (chunkEvents.any { event -> event is ChunkDownloadEvent.ChunkError }) {
                        val errors = chunkEvents
                                .filterIsInstance<ChunkDownloadEvent.ChunkError>()
                                .map { event -> event.error }

                        // If any of the chunks errored out with CancellationException - rethrow it
                        if (errors.any { error -> error is FileCacheException.CancellationException }) {
                            throwCancellationException(url)
                        }

                        // Otherwise rethrow the first exception
                        throw errors.first()
                    }

                    @Suppress("UNCHECKED_CAST")
                    return@flatMap writeChunksToCacheFile(
                            url,
                            chunkEvents as List<ChunkDownloadEvent.ChunkSuccess>,
                            output,
                            startTime
                    )
                }

        // So why are we splitting a reactive stream in two? Because we need to do some
        // additional handling of ChunkedSuccess events but we don't want to do that
        // for Progress event (We want to pass them downstream right away).

        // Merge them back into a single stream
        return Flowable.merge(skipEvents, successEvents)
                .map { cde ->
                    // Map ChunkDownloadEvent to FileDownloadResult
                    return@map when (cde) {
                        is ChunkDownloadEvent.Success -> {
                            FileDownloadResult.Success(
                                    cde.output,
                                    cde.requestTime
                            )
                        }
                        is ChunkDownloadEvent.Progress -> {
                            FileDownloadResult.Progress(
                                    cde.downloaderIndex,
                                    cde.downloaded,
                                    cde.chunkSize
                            )
                        }
                        is ChunkDownloadEvent.ChunkError,
                        is ChunkDownloadEvent.ChunkSuccess -> {
                            throw RuntimeException("Not used, ${cde.javaClass.name}")
                        }
                    }
                }
    }

    private fun processChunks(
            url: String,
            totalDownloaded: AtomicLong,
            chunkIndex: Int,
            canceled: AtomicBoolean,
            chunk: Chunk,
            totalChunksCount: Int
    ): Flowable<ChunkDownloadEvent> {
        BackgroundUtils.ensureBackgroundThread()

        if (isRequestStoppedOrCanceled(url)) {
            throwCancellationException(url)
        }

        // Download each chunk separately in parallel
        return downloadChunk(url, chunk, totalChunksCount)
                .subscribeOn(workerScheduler)
                .observeOn(workerScheduler)
                .map { response -> ChunkResponse(chunk, response) }
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
                            chunkIndex,
                            totalChunksCount,
                            canceled
                    )
                }
                // Retry on IO error mechanism. Apply it to each chunk individually
                // instead of applying it to all chunks. Do not use it if the exception
                // is CancellationException
                .retry(MAX_RETRIES) { error ->
                    val retry = error !is FileCacheException.CancellationException
                            && error is IOException

                    if (retry) {
                        log(TAG, "Retrying chunk ($chunk) for url ${url}, " +
                                "error = ${error.javaClass.simpleName}, msg = ${error.message}")
                    }

                    retry
                }
    }

    private fun writeChunksToCacheFile(
            url: String,
            chunkSuccessEvents: List<ChunkDownloadEvent.ChunkSuccess>,
            output: AbstractFile,
            requestStartTime: Long
    ): Flowable<ChunkDownloadEvent> {
        return Flowable.fromCallable {
            if (ChanSettings.verboseLogs.get()) {
                log(TAG, "writeChunksToCacheFile called ($url), chunks count = ${chunkSuccessEvents.size}")
            }

            try {
                // Must be sorted in ascending order!!!
                val sortedChunkEvents = chunkSuccessEvents.sortedBy { event -> event.chunk.start }

                if (!fileManager.exists(output)) {
                    throw FileCacheException.OutputFileDoesNotExist(output.getFullPath())
                }

                fileManager.getOutputStream(output)?.use { outputStream ->
                    // Iterate each chunk and write it to the output file
                    for (chunkEvent in sortedChunkEvents) {
                        val chunkFile = chunkEvent.chunkCacheFile

                        if (!fileManager.exists(chunkFile)) {
                            throw FileCacheException.ChunkFileDoesNotExist(chunkFile.getFullPath())
                        }

                        fileManager.getInputStream(chunkFile)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        } ?: throw FileCacheException.CouldNotGetInputStreamException(
                                chunkFile.getFullPath(),
                                true,
                                fileManager.isFile(chunkFile),
                                fileManager.canRead(chunkFile)
                        )
                    }

                    outputStream.flush()
                } ?: throw FileCacheException.CouldNotGetOutputStreamException(
                        output.getFullPath(),
                        true,
                        fileManager.isFile(output),
                        fileManager.canRead(output)
                )
            } finally {
                // In case of success or an error we want delete all chunk files
                chunkSuccessEvents.forEach { event ->
                    deleteChunkFile(event.chunkCacheFile)
                }
            }

            // Mark file as downloaded
            markFileAsDownloaded(url)

            val requestTime = System.currentTimeMillis() - requestStartTime
            return@fromCallable ChunkDownloadEvent.Success(output, requestTime)
        }
    }

    private fun deleteChunkFile(chunkFile: RawFile) {
        if (!fileManager.delete(chunkFile)) {
            logError(TAG, "Couldn't delete chunk file: ${chunkFile.getFullPath()}")
        }
    }

    private fun pipeChunk(
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
                if (ChanSettings.verboseLogs.get()) {
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
                        throwCancellationException(url)
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
                                throwCancellationException(url)
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
                    val state = getState(url)
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
                    throwCancellationException(url)
                }

                if (isRequestStoppedOrCanceled(url)) {
                    throwCancellationException(url)
                }

                val read = bufferedSource.read(buffer, BUFFER_SIZE)
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
                throwCancellationException(url)
            }

            if (ChanSettings.verboseLogs.get()) {
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

    /**
     * Marks current CancelableDownload as canceled and throws CancellationException
     * */
    private fun throwCancellationException(url: String): Nothing {
        activeDownloads.get(url)?.cancelableDownload?.cancel()
        throw FileCacheException.CancellationException(getState(url), url)
    }

    private fun downloadChunk(
            url: String,
            chunk: Chunk,
            totalChunksCount: Int
    ): Flowable<Response> {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: throwCancellationException(url)

        if (chunk.isWholeFile() && totalChunksCount > 1) {
            throw IllegalStateException("downloadChunk() Bad amount of chunks, " +
                    "should be only one but actual = $totalChunksCount")
        }

        if (ChanSettings.verboseLogs.get()) {
            log(TAG, "Start downloading ($url), chunk ${chunk.start}..${chunk.end}")
        }

        val builder = Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT)

        if (!chunk.isWholeFile()) {
            // If chunk.isWholeFile == true that means that either the file size is too small to use
            // chunked downloading (less than [FileCacheV2.MIN_CHUNK_SIZE]) or the server does not
            // support Partial Content or the user turned off chunked file downloading, or we
            // couldn't send HEAD request (it was timed out) so we should download it normally.
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

                emitter.tryOnError(FileCacheException.CancellationException(getState(url), url))
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
                                FileCacheException.CancellationException(getState(url), url)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (ChanSettings.verboseLogs.get()) {
                        val diff = System.currentTimeMillis() - startTime
                        log(TAG, "Got chunk response in ($url) ${chunk.start}..${chunk.end} in ${diff}ms")
                    }

                    emitter.onNext(response)
                    emitter.onComplete()
                }
            })
        }, BackpressureStrategy.BUFFER)
    }

    private sealed class ChunkDownloadEvent {
        class Success(val output: AbstractFile, val requestTime: Long) : ChunkDownloadEvent()
        class ChunkSuccess(val chunkCacheFile: RawFile, val chunk: Chunk) : ChunkDownloadEvent()
        class ChunkError(val error: Throwable) : ChunkDownloadEvent()
        class Progress(val downloaderIndex: Int, val downloaded: Long, val chunkSize: Long) : ChunkDownloadEvent()
    }

    private data class ChunkResponse(
            val chunk: Chunk,
            val response: Response
    )

    companion object {
        private const val TAG = "ConcurrentChunkedFileDownloader"
        private const val RANGE_HEADER = "Range"
        private const val RANGE_HEADER_VALUE_FORMAT = "bytes=%d-%d"
        private const val NOT_FOUND_STATUS_CODE = 404
    }
}