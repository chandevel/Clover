package com.github.adamantcheese.chan.core.cache

import android.annotation.SuppressLint
import com.github.adamantcheese.chan.core.cache.downloader.*
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.Site.ChunkDownloaderSiteProperties
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.utils.AndroidUtils.getNetworkClass
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.BackgroundUtils.runOnMainThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import com.github.adamantcheese.chan.utils.exhaustive
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class FileCacheV2(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        siteResolver: SiteResolver,
        okHttpClient: OkHttpClient
) {
    private val activeDownloads = ActiveDownloads()

    private val requestQueue = PublishProcessor.create<HttpUrl>()

    private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(4)

    private val normalThreadIndex = AtomicInteger(0)
    private val workerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(
                                Locale.ENGLISH,
                                "FileCacheV2Thread-%d",
                                normalThreadIndex.getAndIncrement()
                        )
                )
            }
    )

    private val partialContentSupportChecker = PartialContentSupportChecker(
            okHttpClient,
            activeDownloads,
            siteResolver,
            1000
    )

    private val chunkDownloader = ChunkDownloader(
            okHttpClient,
            activeDownloads
    )

    private val chunkReader = ChunkPersister(
            fileManager,
            cacheHandler,
            activeDownloads
    )

    private val chunkPersister = ChunkMerger(
            fileManager,
            cacheHandler,
            siteResolver,
            activeDownloads
    )

    private val concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
            fileManager,
            chunkDownloader,
            chunkReader,
            chunkPersister,
            workerScheduler,
            activeDownloads,
            cacheHandler
    )

    init {
        initNormalRxWorkerQueue()
    }

    /**
     * This is a singleton class so we don't care about the disposable since we will never should
     * dispose of this stream
     * */
    @SuppressLint("CheckResult")
    private fun initNormalRxWorkerQueue() {
        requestQueue
                .onBackpressureBuffer()
                .observeOn(workerScheduler)
                .flatMap { url ->
                    return@flatMap Flowable.defer { handleFileDownload(url) }
                            .subscribeOn(workerScheduler)
                            .onErrorReturn { throwable ->
                                ErrorMapper.mapError(url, throwable, activeDownloads)
                            }
                            .map { result -> Pair(url, result) }
                            .doOnNext { (url, result) -> handleResults(url, result) }
                }
                .subscribe({
                    // Do nothing
                }, { error ->
                    throw RuntimeException("FileCacheV2 Uncaught exception!!! " +
                            "workerQueue is in error state now!!! " +
                            "This should not happen!!!, original error = " + error.message)
                }, {
                    throw RuntimeException(
                            "FileCacheV2 workerQueue stream has completed!!! This should not happen!!!"
                    )
                })
    }

    fun isRunning(url: HttpUrl): Boolean {
        return synchronized(activeDownloads) {
            activeDownloads.getState(url) == DownloadState.Running
        }
    }

    /**
     * Enqueue a download request for the given PostImage based on the setting of the chunk count.
     */
    fun enqueueChunkedDownloadFileRequest(
            postImage: PostImage,
            fileSize: Long,
            chunkDownloaderSiteProperties: ChunkDownloaderSiteProperties,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(
                postImage,
                fileSize,
                4.coerceAtMost(chunkDownloaderSiteProperties.maxChunksForSite),
                callback
        )
    }

    /**
     * Enqueue a download request for the given PostImage with only one chunk and default extra information (no file size, no hash).
     */
    fun enqueueNormalDownloadFileRequest(
            postImage: PostImage,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(
                postImage,
                -1L,
                1,
                callback
        )
    }

    @SuppressLint("CheckResult")
    private fun enqueueDownloadFileRequest(
            postImage: PostImage,
            fileSize: Long,
            chunksCount: Int,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(postImage.imageUrl, chunksCount, fileSize, callback)
    }

    /**
     * Enqueue a download request for the given HttpUrl with only one chunk.
     */
    fun enqueueNormalDownloadFileRequest(
            url: HttpUrl,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(url, 1, -1L, callback)
    }

    private fun enqueueDownloadFileRequest(
            url: HttpUrl,
            chunksCount: Int,
            fileSize: Long,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val file: RawFile? = cacheHandler.getOrCreateCacheFile(url)
        if (file == null) {
            runOnMainThread {
                callback?.onFail(IOException("Couldn't get or create cache file"))
                callback?.onEnd()
            }

            return null
        }

        val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
                url,
                callback,
                file,
                chunksCount = chunksCount,
                fileSize = fileSize
        )

        if (alreadyActive) {
            return cancelableDownload
        }

        if (checkAlreadyCached(file, url)) {
            return null
        }

        requestQueue.onNext(url)

        return cancelableDownload
    }

    // For now it is only used in the developer settings so it's okay to block the UI
    fun clearCache() {
        activeDownloads.clear()
        cacheHandler.clearCache()
    }

    private fun checkAlreadyCached(file: RawFile, url: HttpUrl): Boolean {
        if (!cacheHandler.isAlreadyDownloaded(file)) {
            return false
        }

        Logger.vd(this, "File already downloaded, url = ${maskImageUrl(url)}")

        try {
            handleFileImmediatelyAvailable(file, url)
        } finally {
            activeDownloads.remove(url)
        }

        return true
    }

    // FIXME: if a request is added, then immediately canceled, and after that another one is added,
    //  then in case of the first one not being fast enough to get cancelled before the second one
    //  is added - the two of them will get merged and get canceled together.
    //  Maybe I could add a new flag and right in the end when handling terminal events
    //  I could check whether this flag is true or not and if it is re-add this request again?
    private fun getOrCreateCancelableDownload(
            url: HttpUrl,
            callback: FileCacheListener?,
            file: RawFile,
            chunksCount: Int,
            fileSize: Long
    ): Pair<Boolean, CancelableDownload> {
        return synchronized(activeDownloads) {
            val prevRequest = activeDownloads.get(url)
            if (prevRequest != null) {
                Logger.vd(this, "Request ${maskImageUrl(url)} is already active, re-subscribing to it")

                val prevCancelableDownload = prevRequest.cancelableDownload
                if (callback != null) {
                    prevCancelableDownload.addCallback(callback)
                }

                // true means that this request has already been started before and hasn't yet
                // completed so we can just resubscribe to it instead of creating a new one
                return@synchronized true to prevCancelableDownload
            }

            val cancelableDownload = CancelableDownload(url = url)

            if (callback != null) {
                cancelableDownload.addCallback(callback)
            }

            val request = FileDownloadRequest(
                    url,
                    file,
                    AtomicInteger(chunksCount),
                    AtomicLong(0L),
                    AtomicLong(0L),
                    cancelableDownload,
                    fileSize
            )

            activeDownloads.put(url, request)
            return@synchronized false to cancelableDownload
        }
    }

    private fun handleFileImmediatelyAvailable(file: RawFile, url: HttpUrl) {
        val request = activeDownloads.get(url)
                ?: return

        request.cancelableDownload.forEachCallback {
            runOnMainThread {
                onSuccess(file, true)
                onEnd()
            }
        }
    }

    private fun handleResults(url: HttpUrl, result: FileDownloadResult) {
        BackgroundUtils.ensureBackgroundThread()

        try {
            val request = activeDownloads.get(url)
                    ?: return

            if (result.isErrorOfAnyKind()) {
                // Only call cancel when not already canceled and not stopped
                if (result !is FileDownloadResult.Canceled
                        && result !is FileDownloadResult.Stopped) {
                    activeDownloads.get(url)?.cancelableDownload?.cancel()
                }

                purgeOutput(request.url, request.output)
            }

            val networkClass = getNetworkClassOrDefaultText(result)
            val activeDownloadsCount = activeDownloads.count()

            when (result) {
                is FileDownloadResult.Start -> {
                    // Start is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(url, request, false) {
                        onStart(result.chunksCount)
                    }
                }

                // Success
                is FileDownloadResult.Success -> {
                    val (downloaded, total) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(url)

                        val downloaded = activeDownload?.downloaded?.get()
                        val total = activeDownload?.total?.get()

                        Pair(downloaded, total)
                    }

                    if (downloaded == null || total == null) {
                        return
                    }

                    val downloadedString = PostUtils.getReadableFileSize(downloaded)
                    val totalString = PostUtils.getReadableFileSize(total)

                    Logger.vd(this, "Success (" +
                            "downloaded = $downloadedString ($downloaded B), " +
                            "total = $totalString ($total B), " +
                            "took ${result.requestTime}ms, " +
                            "network class = $networkClass, " +
                            "downloads = $activeDownloadsCount" +
                            ") for request $request"
                    )

                    // Trigger cache trimmer after a file has been successfully downloaded
                    cacheHandler.fileWasAdded(total)

                    resultHandler(url, request, true) {
                        onSuccess(result.file, false)
                        onEnd()
                    }
                }
                // Progress
                is FileDownloadResult.Progress -> {
                    val chunkSize = if (result.chunkSize == 0L) {
                        1L
                    } else {
                        result.chunkSize
                    }

                    val percents = (result.downloaded.toFloat() / chunkSize.toFloat()) * 100f
                    val downloadedString = PostUtils.getReadableFileSize(result.downloaded)
                    val totalString = PostUtils.getReadableFileSize(chunkSize)

                    Logger.vd(this,
                            "Progress " +
                                    "chunkIndex = ${result.chunkIndex}, downloaded: (${downloadedString}) " +
                                    "(${result.downloaded} B) / $totalString (${chunkSize} B), " +
                                    "${percents}%) for request $request"
                    )

                    // Progress is not a terminal event so we don't want to remove request from the
                    // activeDownloads
                    resultHandler(url, request, false) {
                        onProgress(result.chunkIndex, result.downloaded, chunkSize)
                    }
                }

                // Cancel
                is FileDownloadResult.Canceled,
                    // Stop (called by WebmStreamingSource to stop downloading a file via FileCache and
                    // continue downloading it via WebmStreamingDataSource)
                is FileDownloadResult.Stopped -> {
                    val (downloaded, total, output) = synchronized(activeDownloads) {
                        val activeDownload = activeDownloads.get(url)

                        val downloaded = activeDownload?.downloaded?.get()
                        val total = activeDownload?.total?.get()
                        val output = activeDownload?.output

                        Triple(downloaded, total, output)
                    }

                    val isCanceled = when (result) {
                        is FileDownloadResult.Canceled -> true
                        is FileDownloadResult.Stopped -> false
                        else -> throw RuntimeException("Must be either Canceled or Stopped")
                    }

                    val causeText = if (isCanceled) {
                        "canceled"
                    } else {
                        "stopped"
                    }

                    Logger.vd(this, "Request $request $causeText, " +
                            "downloaded = $downloaded, " +
                            "total = $total, " +
                            "network class = $networkClass, " +
                            "downloads = $activeDownloadsCount")

                    resultHandler(url, request, true) {
                        if (isCanceled) {
                            onCancel()
                        } else {
                            onStop(output)
                        }

                        onEnd()
                    }
                }
                is FileDownloadResult.KnownException -> {
                    val message = "Exception for request ${request}, " +
                            "network class = $networkClass, downloads = $activeDownloadsCount"

                    Logger.e(this, message, result.fileCacheException)

                    resultHandler(url, request, true) {
                        when (result.fileCacheException) {
                            is FileCacheException.CancellationException -> {
                                throw RuntimeException("Not used")
                            }
                            is FileCacheException.FileNotFoundOnTheServerException -> {
                                onNotFound()
                            }
                            is FileCacheException.CouldNotMarkFileAsDownloaded,
                            is FileCacheException.NoResponseBodyException,
                            is FileCacheException.CouldNotCreateOutputFileException,
                            is FileCacheException.CouldNotGetInputStreamException,
                            is FileCacheException.CouldNotGetOutputStreamException,
                            is FileCacheException.OutputFileDoesNotExist,
                            is FileCacheException.ChunkFileDoesNotExist,
                            is FileCacheException.HttpCodeException,
                            is FileCacheException.BadOutputFileException -> {
                                if (result.fileCacheException is FileCacheException.HttpCodeException
                                        && result.fileCacheException.statusCode == 404) {
                                    throw RuntimeException("This shouldn't be handled here!")
                                }

                                onFail(IOException(result.fileCacheException.message))
                            }
                        }.exhaustive

                        onEnd()
                    }
                }
                is FileDownloadResult.UnknownException -> {
                    val message = logErrorsAndExtractErrorMessage(
                            "FileCacheV2",
                            "Unknown exception",
                            result.error
                    )

                    resultHandler(url, request, true) {
                        onFail(IOException(message))
                        onEnd()
                    }
                }
            }.exhaustive
        } catch (error: Throwable) {
            Logger.e(this, "An error in result handler", error)
        }
    }

    private fun getNetworkClassOrDefaultText(result: FileDownloadResult): String {
        return when (result) {
            is FileDownloadResult.Start,
            is FileDownloadResult.Success,
            FileDownloadResult.Canceled,
            FileDownloadResult.Stopped,
            is FileDownloadResult.KnownException -> getNetworkClass()
            is FileDownloadResult.Progress,
            is FileDownloadResult.UnknownException -> {
                "Unsupported result: ${result::class.java.simpleName}"
            }
        }.exhaustive
    }

    private fun resultHandler(
            url: HttpUrl,
            request: FileDownloadRequest,
            isTerminalEvent: Boolean,
            func: FileCacheListener.() -> Unit
    ) {
        try {
            request.cancelableDownload.forEachCallback {
                runOnMainThread {
                    func()
                }
            }
        } finally {
            if (isTerminalEvent) {
                request.cancelableDownload.clearCallbacks()
                activeDownloads.remove(url)
            }
        }
    }

    private fun handleFileDownload(url: HttpUrl): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
        if (request == null || !request.cancelableDownload.isRunning()) {
            val state = request?.cancelableDownload?.getState()
                    ?: DownloadState.Canceled

            return Flowable.error(FileCacheException.CancellationException(state, url))
        }

        val exists = fileManager.exists(request.output)
        val outputFile = if (!exists) {
            fileManager.create(request.output) as? RawFile
        } else {
            request.output
        }

        val fullPath = request.output.getFullPath()
        if (outputFile == null) {
            return Flowable.error(
                    FileCacheException.CouldNotCreateOutputFileException(fullPath)
            )
        }

        val isFile = fileManager.isFile(outputFile)
        val canWrite = fileManager.canWrite(outputFile)

        if (!isFile || !canWrite) {
            return Flowable.error(
                    FileCacheException.BadOutputFileException(fullPath, exists, isFile, canWrite)
            )
        }

        return partialContentSupportChecker.check(url)
                .observeOn(workerScheduler)
                .toFlowable()
                .flatMap { result ->
                    if (result.notFoundOnServer) {
                        throw FileCacheException.FileNotFoundOnTheServerException()
                    }

                    return@flatMap concurrentChunkedFileDownloader.download(
                            result,
                            url,
                            result.supportsPartialContentDownload
                    )
                }
    }

    private fun purgeOutput(url: HttpUrl, output: RawFile) {
        BackgroundUtils.ensureBackgroundThread()

        val request = activeDownloads.get(url)
                ?: return

        if (request.cancelableDownload.getState() != DownloadState.Canceled) {
            // Not canceled, only purge output when canceled. Do not purge the output file when
            // the state stopped too, because we are gonna use the file for the webm streaming cache.
            return
        }

        Logger.vd(this, "Purging ${maskImageUrl(url)}, file = ${output.getFullPath()}")

        if (!cacheHandler.deleteCacheFile(output)) {
            Logger.e(this, "Could not delete the file in purgeOutput, output = ${output.getFullPath()}")
        }
    }
}