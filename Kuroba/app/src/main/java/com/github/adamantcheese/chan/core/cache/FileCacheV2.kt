package com.github.adamantcheese.chan.core.cache

import android.annotation.SuppressLint
import com.github.adamantcheese.chan.core.cache.downloader.*
import com.github.adamantcheese.chan.core.cache.downloader.DownloaderUtils.isCancellationError
import com.github.adamantcheese.chan.core.kt_extensions.exhaustive
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.file.Segment
import io.reactivex.Flowable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A file downloader with two reactive queues:
 * - One queue is for viewing images/webms/gifs in the gallery
 * - Second queue is for downloading image albums in huge batches (or for the media prefetching feature).
 *
 * This should prevent normal image viewing getting stuck when using media prefetching.
 * In the future this thing will be made 100% reactive (right now it still uses callbacks) as well
 * as the MultiImageView/ImageViewPresenter.
 * */
class FileCacheV2(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val okHttpClient: OkHttpClient
) {
    private val activeDownloads = ActiveDownloads()

    /**
     * We use two rx queues here. One for the normal file/image downloading (like when user clicks a
     * image thumbnail to view a full-size image) and the other queue for when user downloads full
     * image albums or for media-prefetching etc.
     * */
    private val normalRequestQueue = PublishProcessor.create<String>()
    private val batchRequestQueue = PublishProcessor.create<List<String>>()

    private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(3)
    private val requestCancellationThread = Executors.newSingleThreadExecutor()

    private val partialContentSupportChecker = PartialContentSupportChecker(
            okHttpClient,
            activeDownloads
    )
    private val concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
            okHttpClient,
            fileManager,
            ChanSettings.concurrentFileDownloadingThreadCount.get(),
            activeDownloads,
            cacheHandler
    )

    private val threadIndex = AtomicInteger(0)
    private val workerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(THREAD_NAME_FORMAT, threadIndex.getAndIncrement())
                )
            }
    )

    init {
        initNormalRxWorkerQueue()
        initBatchRequestQueue()
    }

    @SuppressLint("CheckResult")
    private fun initNormalRxWorkerQueue() {
        normalRequestQueue
                .observeOn(workerScheduler)
                .onBackpressureBuffer()
                .flatMap { url ->
                    return@flatMap Flowable.defer { handleFileDownload(url) }
                        .subscribeOn(workerScheduler)
                        .onErrorReturn { throwable -> processErrors(throwable) }
                        .map { result -> Pair(url, result) }
                        .doOnNext { (url, result) -> handleResults(url, result) }
                }
                .subscribe({
                    // Do nothing
                }, { error ->
                    throw RuntimeException("Uncaught exception!!! " +
                            "workerQueue is in error state now!!! " +
                            "This should not happen!!!, original error = " + error.message)
                }, {
                    throw RuntimeException(
                            "workerQueue stream has completed!!! This should not happen!!!"
                    )
                })
    }

    @SuppressLint("CheckResult")
    private fun initBatchRequestQueue() {
        batchRequestQueue
                .observeOn(workerScheduler)
                .onBackpressureBuffer()
                .flatMap { urlList ->
                    return@flatMap Flowable.fromIterable(urlList)
                        .subscribeOn(workerScheduler)
                        .flatMap { url ->
                            return@flatMap handleFileDownload(url)
                                .onErrorReturn { throwable -> processErrors(throwable) }
                                .map { result -> Pair(url, result) }
                                .doOnNext { (url, result) ->
                                    handleResults(url, result)
                                }
                        }
                }
                .subscribe({
                    // Do nothing
                }, { error ->
                    throw RuntimeException("Uncaught exception!!! " +
                            "workerQueue is in error state now!!! " +
                            "This should not happen!!!, original error = " + error.message)
                }, {
                    throw RuntimeException(
                            "workerQueue stream has completed!!! This should not happen!!!"
                    )
                })
    }

    fun enqueueMediaPrefetchRequestBatch(
            loadable: Loadable,
            postImageList: List<PostImage>
    ): List<CancelableDownload> {
        if (loadable.isLocal || loadable.isDownloading) {
            throw IllegalArgumentException("Cannot use local thread loadable for prefetching!")
        }

        val urls = mutableListOf<String>()
        val cancelableDownloads = mutableListOf<CancelableDownload>()

        for (postImage in postImageList) {
            val url = postImage.imageUrl.toString()

            val file: RawFile = cacheHandler.getOrCreateCacheFile(url)
                    ?: continue

            val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
                    url,
                    null,
                    file,
                    isBatchDownload = true,
                    isPrefetch = true
            )

            if (alreadyActive) {
                continue
            }

            if (checkAlreadyCached(file, url)) {
                continue
            }

            urls += url
            cancelableDownloads += cancelableDownload
        }

        log(TAG, "Prefetching ${urls.size} files")
        batchRequestQueue.onNext(urls)

        return cancelableDownloads
    }

    fun enqueueDownloadFileRequest(
            loadable: Loadable,
            postImage: PostImage,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(
                loadable,
                postImage,
                false,
                callback
        )
    }

    fun enqueueDownloadFileRequest(
            loadable: Loadable,
            postImage: PostImage,
            isBatchDownload: Boolean,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val url = postImage.imageUrl.toString()

        if (loadable.isLocal || loadable.isDownloading) {
            log(TAG, "Handling local thread file, url = $url")

            if (callback == null) {
                logError(TAG, "Callback is null for a local thread")
                return null
            }

            return try {
                handleLoadThreadFile(loadable, postImage, callback)
            } catch (error: Throwable) {
                logError(TAG, "Error while trying to load local thread file", error)

                callback.onFail(Exception(error))
                callback.onEnd()

                null
            }
        }

        return enqueueDownloadFileRequest(url, isBatchDownload, callback)
    }

    fun enqueueDownloadFileRequest(
            url: String,
            callback: FileCacheListener?
    ): CancelableDownload? {
        return enqueueDownloadFileRequest(url, false, callback)
    }

    private fun enqueueDownloadFileRequest(
            url: String,
            isBatchDownload: Boolean,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val file: RawFile? = cacheHandler.getOrCreateCacheFile(url)
        if (file == null) {
            callback?.onFail(IOException("Couldn't get or create cache file"))
            callback?.onEnd()

            return null
        }

        val (alreadyActive, cancelableDownload) = getOrCreateCancelableDownload(
                url,
                callback,
                file,
                isBatchDownload = isBatchDownload,
                isPrefetch = false
        )

        if (alreadyActive) {
            return cancelableDownload
        }

        if (checkAlreadyCached(file, url)) {
            return null
        }

        log(TAG, "Downloading a file, url = $url")
        normalRequestQueue.onNext(url)

        return cancelableDownload
    }

    // For now it is only used in the developer settings so it's okay to block the UI
    fun clearCache() {
        activeDownloads.clear()
        cacheHandler.clearCache()
    }

    private fun checkAlreadyCached(file: RawFile, url: String): Boolean {
        if (!cacheHandler.isAlreadyDownloaded(file)) {
            return false
        }

        log(TAG, "File already downloaded, url = $url")

        try {
            handleFileImmediatelyAvailable(file, url)
        } finally {
            activeDownloads.remove(url)
        }

        return true
    }

    private fun getOrCreateCancelableDownload(
            url: String,
            callback: FileCacheListener?,
            file: RawFile,
            isBatchDownload: Boolean,
            isPrefetch: Boolean
    ): Pair<Boolean, CancelableDownload> {
        return synchronized(activeDownloads) {
            if (activeDownloads.containsKey(url)) {
                val prevRequest = checkNotNull(activeDownloads.get(url)) {
                    "Request was removed inside a synchronized block. " +
                            "Apparently it's not thread-safe anymore"
                }

                log(TAG, "Request ${url} is already active, subscribing to it")

                val prevCancelableDownload = prevRequest.cancelableDownload
                if (callback != null) {
                    prevCancelableDownload.addCallback(callback)
                }

                return@synchronized true to prevCancelableDownload
            }

            val cancelableDownload = CancelableDownload(
                    url = url,
                    requestCancellationThread = requestCancellationThread,
                    isPartOfBatchDownload = AtomicBoolean(isPrefetch || isBatchDownload)
            )

            if (callback != null) {
                cancelableDownload.addCallback(callback)
            }

            val request = FileDownloadRequest(
                    url,
                    file,
                    AtomicLong(0L),
                    AtomicLong(0L),
                    cancelableDownload
            )

            activeDownloads.put(url, request)
            return@synchronized false to cancelableDownload
        }
    }

    private fun handleLoadThreadFile(
            loadable: Loadable,
            postImage: PostImage,
            callback: FileCacheListener
    ): CancelableDownload? {
        val filename = ThreadSaveManager.formatOriginalImageName(
                postImage.serverFilename,
                postImage.extension
        )

        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory::class.java)) {
            logError(TAG, "handleLoadThreadFile() Base local threads directory does not exist")

            callback.onFail(IOException("Base local threads directory does not exist"))
            callback.onEnd()

            return null
        }

        val baseDirFile = fileManager.newBaseDirectoryFile(
                LocalThreadsBaseDirectory::class.java
        )

        if (baseDirFile == null) {
            logError(TAG, "handleLoadThreadFile() fileManager.newLocalThreadFile() returned null")

            callback.onFail(IOException("Couldn't create a file inside local threads base directory"))
            callback.onEnd()

            return null
        }

        val imagesSubDirSegments = ThreadSaveManager.getImagesSubDir(loadable)
        val segments: MutableList<Segment> = ArrayList(imagesSubDirSegments).apply {
            add(FileSegment(filename))
        }

        val localImgFile = baseDirFile.clone(segments)
        val isLocalFileOk = fileManager.exists(localImgFile)
                && fileManager.isFile(localImgFile)
                && fileManager.canRead(localImgFile)

        if (isLocalFileOk) {
            handleLocalThreadFileImmediatelyAvailable(localImgFile, postImage, callback)
        } else {
            logError(TAG, "Cannot load saved image from the disk, path: " + localImgFile.getFullPath())

            callback.onFail(
                    IOException("Couldn't load saved image from the disk, path: "
                            + localImgFile.getFullPath())
            )

            callback.onEnd()
        }

        return null
    }

    private fun handleFileImmediatelyAvailable(file: RawFile, url: String) {
        val request = activeDownloads.get(url)
                ?: return

        request.cancelableDownload.forEachCallback {
            BackgroundUtils.runOnUiThread {
                onSuccess(file)
                onEnd()
            }
        }
    }

    private fun handleLocalThreadFileImmediatelyAvailable(
            file: AbstractFile,
            postImage: PostImage,
            callback: FileCacheListener?
    ) {
        if (file is RawFile) {
            // Regular Java File
            BackgroundUtils.runOnUiThread {
                callback?.onSuccess(file)
                callback?.onEnd()
            }
        } else {
            // SAF file
            try {
                val resultFile = cacheHandler.getOrCreateCacheFile(postImage.imageUrl.toString())
                if (resultFile == null) {
                    callback?.onFail(IOException("Couldn't get or create cache file"))
                    callback?.onEnd()
                    return
                }

                if (!fileManager.copyFileContents(file, resultFile)) {
                    if (!cacheHandler.deleteCacheFile(resultFile)) {
                        Logger.e(TAG, "Couldn't delete cache file ${resultFile.getFullPath()}")
                    }

                    val error = IOException(
                            "Could not copy external SAF file into internal cache file, " +
                                    "externalFile = " + file.getFullPath() +
                                    ", resultFile = " + resultFile.getFullPath()
                    )

                    callback?.onFail(error)
                    callback?.onEnd()
                    return
                }

                if (!cacheHandler.markFileDownloaded(resultFile)) {
                    callback?.onFail(FileCacheException.CouldNotMarkFileAsDownloaded(resultFile))
                    callback?.onEnd()
                    return
                }

                BackgroundUtils.runOnUiThread {
                    callback?.onSuccess(resultFile)
                    callback?.onEnd()
                }
            } catch (e: IOException) {
                logError(TAG, "Error while trying to create a new random cache file", e)

                BackgroundUtils.runOnUiThread {
                    callback?.onFail(e)
                    callback?.onEnd()
                }
            }
        }
    }

    private fun handleResults(url: String, result: FileDownloadResult) {
        BackgroundUtils.ensureBackgroundThread()

        try {
            val request = activeDownloads.get(url)
                    ?: return

            // We handle Progress separately because we don't want to also call removeCacheFile()
            // after every Progress event (there will likely be a lot of them)
            if (result is FileDownloadResult.Progress) {
                handleProgressResult(result, request)
                return
            }

            if (result.isErrorOfAnyKind()) {
                // Only call cancel when not already canceled and not stopped
                if (result !is FileDownloadResult.Canceled
                        && result !is FileDownloadResult.Stopped) {
                    activeDownloads.get(url)?.cancelableDownload?.cancel()
                }

                purgeOutput(request.url, request.output)
            }

            handleSuccessOrError(result, url, request)
        } catch (error: Throwable) {
            Logger.e(TAG, "An error in result handler", error)
        }
    }

    private fun handleProgressResult(result: FileDownloadResult.Progress, request: FileDownloadRequest) {
        when (result) {
            is FileDownloadResult.Progress.NormalProgress -> {
                val total = if (result.total == 0L) {
                    1L
                } else {
                    result.total
                }

                val percents = (result.downloaded.toFloat() / total.toFloat()) * 100f
                val downloadedString = PostUtils.getReadableFileSize(result.downloaded)
                val totalString = PostUtils.getReadableFileSize(total)

                log(TAG, "Progress (" +
                        "${downloadedString} (${result.downloaded} B) / ${totalString} (${total} B)," +
                        " ${percents}%) for request ${request}"
                )

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onProgress(result.downloaded, total)
                    }
                }
            }
            is FileDownloadResult.Progress.ChunkProgress -> {
                // TODO("Not implemented")
            }
        }
    }

    private fun handleSuccessOrError(
            result: FileDownloadResult,
            url: String,
            request: FileDownloadRequest
    ) {
        when (result) {
            // Success/Progress
            is FileDownloadResult.Success -> {
                if (!handleSuccess(url, result, request)) {
                    return
                } else {
                    // Do nothing. Exhaustive when requires us to handle all branches of nested
                    // if/else statements
                    Unit
                }
            }
            is FileDownloadResult.Progress -> {
                // Do nothing, already handled above
            }

            // Cancel
            is FileDownloadResult.Canceled -> {
                log(TAG, "Request ${request} canceled")

                resultHandler(url, request) {
                    onCancel()
                    onEnd()
                }
            }
            is FileDownloadResult.Stopped -> {
                val (downloaded, total, output) = synchronized(activeDownloads) {
                    val activeDownload =  activeDownloads.get(url)

                    val downloaded = activeDownload?.downloaded?.get()
                    val total = activeDownload?.total?.get()
                    val output = activeDownload?.output

                    Triple(downloaded, total, output)
                }

                log(TAG, "Request ${request} stopped, downloaded = $downloaded, total = $total")

                resultHandler(url, request) {
                    onStop(output)
                    onEnd()
                }
            }
            is FileDownloadResult.KnownException -> {
                logError(TAG, "Exception for request ${request}", result.fileCacheException)

                // TODO: exception handling

                resultHandler(url, request) {
                    onFail(IOException(result.fileCacheException))
                    onEnd()
                }
            }
            is FileDownloadResult.UnknownException -> TODO()


            // Errors
//            is FileDownloadResult.NotFound -> {
//                logError(TAG, "File not found for request ${request}")
//
//                resultHandler(url, request) {
//                    onFail(IOException("File not found"))
//                    onEnd()
//                }
//            }
//            is FileDownloadResult.BadOutputFileError -> {
//                val exception = IOException("Bad output file: " +
//                        "exists = ${result.exists}, " +
//                        "isFile = ${result.isFile}, " +
//                        "canWrite = ${result.canWrite}"
//                )
//
//                logError(TAG, "Bad output file error for request ${request}", exception)
//
//                resultHandler(url, request) {
//                    onFail(exception)
//                    onEnd()
//                }
//            }
//            is FileDownloadResult.HttpCodeIOError -> {
//                val exception = if (result.statusCode != 404) {
//                    IOException("Bad response status code: ${result.statusCode}")
//                } else {
//                    FileNotFoundOnTheServerException()
//                }
//
//                logError(TAG, "Http code error for request ${request}, status code = ${result.statusCode}")
//
//                resultHandler(url, request) {
//                    onFail(exception)
//                    onEnd()
//                }
//            }
//            is FileDownloadResult.NoResponseBodyError -> {
//                val exception = IOException("No response body returned for request ${request}")
//                logError(TAG, "No response body returned for request ${request}")
//
//                resultHandler(url, request) {
//                    onFail(exception)
//                    onEnd()
//                }
//            }
//            is FileDownloadResult.CouldNotGetOutputStreamError -> {
//                logError(TAG, "CouldNotGetOutputStreamError(" +
//                        "exists = ${result.exists}, " +
//                        "isFile = ${result.isFile}, " +
//                        "canWrite = ${result.canWrite}) for request ${request}")
//
//                resultHandler(url, request) {
//                    onFail(IOException("Could not get output stream"))
//                    onEnd()
//                }
//            }
//            is FileDownloadResult.CouldNotCreateOutputFile -> {
//                logError(TAG, "CouldNotCreateOutputFile for request ${request}, " +
//                        "output file path = ${result.filePath}")
//
//                resultHandler(url, request) {
//                    onFail(IOException("Could not create output file"))
//                    onEnd()
//                }
//            }
//            FileDownloadResult.DoesNotSupportPartialContent -> {
//                logError(TAG, "DoesNotSupportPartialContent")
//                // TODO("Not implemented")
//            }
//            is FileDownloadResult.CouldNotGetInputStreamError -> {
//                // TODO("Not implemented")
//                logError(TAG, "CouldNotGetInputStreamError")
//            }
        }.exhaustive
    }

    private fun handleSuccess(
            url: String,
            result: FileDownloadResult.Success,
            request: FileDownloadRequest
    ): Boolean {
        when (result) {
            is FileDownloadResult.Success.NormalSuccess -> {
                val (downloaded, total) = synchronized(activeDownloads) {
                    val activeDownload = activeDownloads.get(url)

                    val downloaded = activeDownload?.downloaded?.get()
                    val total = activeDownload?.total?.get()

                    Pair(downloaded, total)
                }

                if (downloaded == null || total == null) {
                    return false
                }

                val downloadedString = PostUtils.getReadableFileSize(downloaded)
                val totalString = PostUtils.getReadableFileSize(total)

                log(TAG, "Success (" +
                        "downloaded = ${downloadedString} ($downloaded B), " +
                        "total = ${totalString} ($total B), " +
                        "took ${result.requestTime}ms" +
                        ") for request ${request}"
                )

                // Trigger cache trimmer after a file has been successfully downloaded
                cacheHandler.fileWasAdded(total)

                resultHandler(url, request) {
                    onSuccess(result.file)
                    onEnd()
                }
            }
            is FileDownloadResult.Success.ChunkSuccess -> {
                // Do nothing, this is already handled in ConcurrentChunkedFileDownloader
            }
        }

        return true
    }

    private fun resultHandler(
            url: String,
            request: FileDownloadRequest,
            func: FileCacheListener.() -> Unit
    ) {
        request.cancelableDownload.forEachCallback {
            BackgroundUtils.runOnUiThread {
                func()
            }
        }

        request.cancelableDownload.clearCallbacks()
        activeDownloads.remove(url)
    }

    private fun handleFileDownload(url: String): Flowable<FileDownloadResult> {
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
                .toFlowable()
                .flatMap { result ->
                    return@flatMap concurrentChunkedFileDownloader.download(
                            result,
                            url,
                            result.supportsPartialContentDownload
                    )
                }
    }

    private fun processErrors(error: Throwable): FileDownloadResult? {
        if (isCancellationError(error)) {
            return FileDownloadResult.Canceled
        }

        if (error is FileCacheException.StoppedException) {
            return FileDownloadResult.Stopped
        }

        if (error is FileCacheException) {
            return FileDownloadResult.KnownException(error)
        }

        return FileDownloadResult.UnknownException(error)
    }

    private fun purgeOutput(url: String, output: RawFile) {
        BackgroundUtils.ensureBackgroundThread()

        val request = checkNotNull(activeDownloads.get(url)) {
            "Request was removed inside a synchronized block. " +
                    "Apparently it's not thread-safe anymore"
        }

        if (request.cancelableDownload.getState() != DownloadState.Canceled) {
            // Not canceled, only purge output when canceled
            return
        }

        log(TAG, "Purging $url, file = ${output.getFullPath()}")

        if (!cacheHandler.deleteCacheFile(output)) {
            logError(TAG, "Could not delete the file in purgeOutput, output = ${output.getFullPath()}")
        }
    }

    companion object {
        private const val TAG = "FileCacheV2"
        private const val THREAD_NAME_FORMAT = "FileCacheV2Thread-%d"
        const val MIN_CHUNK_SIZE = 1024L * 8L // 8 KB
    }
}