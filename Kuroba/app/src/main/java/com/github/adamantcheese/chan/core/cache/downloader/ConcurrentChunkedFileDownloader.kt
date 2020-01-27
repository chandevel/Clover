package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.Flowable
import io.reactivex.Scheduler
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

internal class ConcurrentChunkedFileDownloader @Inject constructor(
        private val fileManager: FileManager,
        private val chunkDownloader: ChunkDownloader,
        private val chunkPersister: ChunkPersister,
        private val chunkMerger: ChunkMerger,
        private val workerScheduler: Scheduler,
        private val verboseLogs: Boolean,
        activeDownloads: ActiveDownloads,
        cacheHandler: CacheHandler
) : FileDownloader(activeDownloads, cacheHandler) {

    override fun download(
            partialContentCheckResult: PartialContentCheckResult,
            url: String,
            chunked: Boolean
    ): Flowable<FileDownloadResult> {
        val output = activeDownloads.get(url)
                ?.output
                ?: activeDownloads.throwCancellationException(url)

        if (!fileManager.exists(output)) {
            return Flowable.error(IOException("Output file does not exist!"))
        }

        // We can't use Partial Content if we don't know the file size
        val chunksCount = if (chunked && partialContentCheckResult.couldDetermineFileSize()) {
            activeDownloads.get(url)
                    ?.chunksCount
                    ?.get()
                    ?: activeDownloads.throwCancellationException(url)
        } else {
            // Update the chunksCount because it changed due to HEAD request failure or some other
            // kind of problem
            activeDownloads.get(url)?.chunksCount?.set(1)
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
                Flowable.defer {
                    return@defer downloadInternal(
                            url,
                            chunks,
                            partialContentCheckResult,
                            output
                    )
                }
                        .doOnSubscribe { log(TAG, "Starting downloading (${url})") }
                        .doOnComplete {
                            log(TAG, "Completed downloading (${url})")
                            removeChunksFromDisk(url)
                        }
                        .doOnError { error ->
                            logErrorsAndExtractErrorMessage(
                                    TAG,
                                    "Error while trying to download",
                                    error
                            )

                            removeChunksFromDisk(url)
                        }
                        .subscribeOn(workerScheduler)
        )
    }

    private fun removeChunksFromDisk(url: String) {
        val chunks = activeDownloads.getChunks(url)
        if (chunks.isEmpty()) {
            return
        }

        for (chunk in chunks) {
            val chunkFile = cacheHandler.getChunkCacheFileOrNull(chunk.start, chunk.end, url)
                    ?: continue

            if (fileManager.delete(chunkFile)) {
                log(TAG, "Deleted chunk file ${chunkFile.getFullPath()}")
            } else {
                logError(TAG, "Couldn't delete chunk file ${chunkFile.getFullPath()}")
            }
        }

        activeDownloads.clearChunks(url)
    }

    private fun downloadInternal(
            url: String,
            chunks: List<Chunk>,
            partialContentCheckResult: PartialContentCheckResult,
            output: RawFile
    ): Flowable<FileDownloadResult> {
        if (verboseLogs) {
            log(TAG, "File (${url}) was split into chunks: ${chunks}")
        }

        if (!partialContentCheckResult.couldDetermineFileSize() && chunks.size != 1) {
            throw IllegalStateException("The size of the file is unknown but chunks size is not 1, " +
                    "size = ${chunks.size}, chunks = $chunks")
        }

        if (isRequestStoppedOrCanceled(url)) {
            activeDownloads.throwCancellationException(url)
        }

        if (partialContentCheckResult.couldDetermineFileSize()) {
            activeDownloads.updateTotalLength(url, partialContentCheckResult.length)
        }

        val startTime = System.currentTimeMillis()
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
                        activeDownloads.throwCancellationException(url)
                    }

                    if (chunkEvents.any { event -> event is ChunkDownloadEvent.ChunkError }) {
                        val errors = chunkEvents
                                .filterIsInstance<ChunkDownloadEvent.ChunkError>()
                                .map { event -> event.error }

                        // If any of the chunks errored out with CancellationException - rethrow it
                        if (errors.any { error -> error is FileCacheException.CancellationException }) {
                            activeDownloads.throwCancellationException(url)
                        }

                        // Otherwise rethrow the first exception
                        throw errors.first()
                    }

                    @Suppress("UNCHECKED_CAST")
                    return@flatMap chunkMerger.mergeChunksIntoCacheFile(
                            url,
                            chunkEvents as List<ChunkDownloadEvent.ChunkSuccess>,
                            output,
                            startTime
                    )
                }

        // So why are we splitting a reactive stream in two? Because we need to do some
        // additional handling of ChunkSuccess events but we don't want to do that
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
                                    cde.chunkIndex,
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
            chunk: Chunk,
            totalChunksCount: Int
    ): Flowable<ChunkDownloadEvent> {
        BackgroundUtils.ensureBackgroundThread()

        if (isRequestStoppedOrCanceled(url)) {
            activeDownloads.throwCancellationException(url)
        }

        // Download each chunk separately in parallel
        return chunkDownloader.downloadChunk(url, chunk, totalChunksCount)
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
                    return@flatMap chunkPersister.storeChunkInFile(
                            url,
                            chunkResponse,
                            totalDownloaded,
                            chunkIndex,
                            totalChunksCount
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

    companion object {
        private const val TAG = "ConcurrentChunkedFileDownloader"
    }
}