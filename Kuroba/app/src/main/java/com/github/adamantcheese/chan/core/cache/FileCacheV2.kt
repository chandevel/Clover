package com.github.adamantcheese.chan.core.cache

import android.annotation.SuppressLint
import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.core.kt_extensions.exhaustive
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.PostUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.file.Segment
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.disposables.Disposable
import io.reactivex.processors.PublishProcessor
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okhttp3.internal.closeQuietly
import okhttp3.internal.http2.StreamResetException
import okio.*
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    @GuardedBy("itself")
    private val activeDownloads = hashMapOf<String, FileDownloadRequest>()

    /**
     * We use two rx queues here. One for the normal file/image downloading (like when user clicks a
     * image thumbnail to view a full-size image) and the other queue for when user downloads full
     * image albums or for media-prefetching etc.
     * */
    private val normalRequestQueue = PublishProcessor.create<String>()
    private val batchRequestQueue = PublishProcessor.create<List<String>>()

    private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(4)
    private val requestCancellationThread = Executors.newSingleThreadExecutor()

    private val normalThreadIndex = AtomicInteger(0)
    private val normalWorkerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(NORMAL_THREAD_NAME_FORMAT, normalThreadIndex.getAndIncrement())
                )
            }
    )

    private val batchThreadIndex = AtomicInteger(0)
    private val batchWorkerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(BATCH_THREAD_NAME_FORMAT, batchThreadIndex.getAndIncrement())
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
                .observeOn(normalWorkerScheduler)
                .onBackpressureBuffer()
                .flatMap { requestId ->
                    return@flatMap Flowable.defer { handleFileDownload(requestId) }
                            .subscribeOn(normalWorkerScheduler)
                            .onErrorReturn { throwable ->
                                if (throwable is CancellationException) {
                                    return@onErrorReturn FileDownloadResult.Canceled
                                }

                                return@onErrorReturn FileDownloadResult.Exception(throwable)
                            }
                            .map { result -> Pair(requestId, result) }
                            .doOnNext { (requestId, result) -> handleResults(requestId, result) }
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
                .observeOn(batchWorkerScheduler)
                .onBackpressureBuffer()
                .flatMap { requestIdList ->
                    return@flatMap Flowable.fromIterable(requestIdList)
                            .subscribeOn(normalWorkerScheduler)
                            .flatMap { requestId ->
                                return@flatMap handleFileDownload(requestId)
                                        .onErrorReturn { throwable ->
                                            if (throwable is CancellationException) {
                                                return@onErrorReturn FileDownloadResult.Canceled
                                            }

                                            return@onErrorReturn FileDownloadResult.Exception(throwable)
                                        }
                                        .map { result -> Pair(requestId, result) }
                                        .doOnNext { (requestId, result) ->
                                            handleResults(requestId, result)
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

            val file: RawFile = cacheHandler.getOrCreate(url)
                    ?: continue

            val cancelableDownload = getOrCreateCancelableDownload(
                    url,
                    null,
                    file,
                    true
            )

            if (checkAlreadyCached(file, url)) {
                continue
            }

            urls += url
            cancelableDownloads += cancelableDownload
        }

        log("Prefetching ${urls.size} files")
        batchRequestQueue.onNext(urls)

        return cancelableDownloads
    }

    fun enqueueDownloadFileRequest(
            loadable: Loadable,
            postImage: PostImage,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val url = postImage.imageUrl.toString()

        if (loadable.isLocal || loadable.isDownloading) {
            log("Handling local thread file, url = $url")

            if (callback == null) {
                logError("Callback is null for a local thread")
                return null
            }

            return try {
                handleLoadThreadFile(loadable, postImage, callback)
            } catch (error: Throwable) {
                logError("Error while trying to load local thread file", error)

                callback.onFail(Exception(error))
                callback.onEnd()

                null
            }
        }

        return enqueueDownloadFileRequest(url, callback)
    }

    fun enqueueDownloadFileRequest(
            url: String,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val file: RawFile? = cacheHandler.getOrCreate(url)
        if (file == null) {
            callback?.onFail(IOException("Couldn't get or create cache file"))
            callback?.onEnd()

            return null
        }

        val cancelableDownload = getOrCreateCancelableDownload(
                url,
                callback,
                file,
                false
        )

        if (checkAlreadyCached(file, url)) {
            return null
        }

        log("Downloading a file, url = $url")
        normalRequestQueue.onNext(url)

        return cancelableDownload
    }

    private fun checkAlreadyCached(file: RawFile, url: String): Boolean {
        if (!cacheHandler.isAlreadyDownloaded(file)) {
            return false
        }

        log("File already downloaded, url = $url")

        try {
            handleFileImmediatelyAvailable(file, url)
        } finally {
            synchronized(activeDownloads) {
                val request = activeDownloads[url]
                if (request != null) {
                    request.cancelableDownload.clearCallbacks()
                    activeDownloads.remove(url)
                }
            }
        }

        return true
    }

    private fun getOrCreateCancelableDownload(
            url: String,
            callback: FileCacheListener?,
            file: RawFile,
            isPrefetch: Boolean
    ): CancelableDownload {
        return synchronized(activeDownloads) {
            if (activeDownloads.containsKey(url)) {
                val prevRequest = checkNotNull(activeDownloads[url]) {
                    "Request was removed inside a synchronized block. " +
                            "Apparently it's not thread-safe anymore"
                }

                log("Request ${url} is already active, subscribing to it")

                val prevCancelableDownload = prevRequest.cancelableDownload
                if (callback != null) {
                    prevCancelableDownload.addCallback(callback)
                }

                return@synchronized prevCancelableDownload
            }

            val cancelableDownload = CancelableDownload(
                    url = url,
                    isPartOfBatchDownload = AtomicBoolean(isPrefetch)
            )

            if (callback != null) {
                cancelableDownload.addCallback(callback)
            }

            activeDownloads[url] = FileDownloadRequest(
                    url,
                    file,
                    AtomicLong(0L),
                    AtomicLong(0L),
                    cancelableDownload
            )

            return@synchronized cancelableDownload
        }
    }

    // For now it is only used in the developer settings so it's okay to block the UI
    fun clearCache() {
        synchronized(activeDownloads) {
            activeDownloads.values.forEach { download ->
                download.cancelableDownload.cancel()
                download.cancelableDownload.clearCallbacks()
            }

            activeDownloads.clear()
        }

        cacheHandler.clearCache()
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
            logError("handleLoadThreadFile() Base local threads directory does not exist")

            callback.onFail(IOException("Base local threads directory does not exist"))
            callback.onEnd()

            return null
        }

        val baseDirFile = fileManager.newBaseDirectoryFile(
                LocalThreadsBaseDirectory::class.java
        )

        if (baseDirFile == null) {
            logError("handleLoadThreadFile() fileManager.newLocalThreadFile() returned null")

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
            logError("Cannot load saved image from the disk, path: " + localImgFile.getFullPath())

            callback.onFail(
                    IOException("Couldn't load saved image from the disk, path: "
                            + localImgFile.getFullPath())
            )

            callback.onEnd()
        }

        return null
    }

    private fun handleFileImmediatelyAvailable(file: RawFile, requestId: String) {
        val request = synchronized(activeDownloads) { activeDownloads[requestId] }
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
                val resultFile = cacheHandler.getOrCreate(postImage.imageUrl.toString())
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
                    callback?.onFail(CouldNotMarkFileAsDownloaded(resultFile))
                    callback?.onEnd()
                    return
                }

                BackgroundUtils.runOnUiThread {
                    callback?.onSuccess(resultFile)
                    callback?.onEnd()
                }
            } catch (e: IOException) {
                logError("Error while trying to create a new random cache file", e)

                BackgroundUtils.runOnUiThread {
                    callback?.onFail(e)
                    callback?.onEnd()
                }
            }
        }
    }

    private fun handleResults(requestId: String, result: FileDownloadResult) {
        BackgroundUtils.ensureBackgroundThread()

        try {
            val request = synchronized(activeDownloads) { activeDownloads[requestId] }
                    ?: return

            // We handle Progress separately because we don't want to also call removeCacheFile()
            // after every Progress event (there will likely be a lot of them)
            if (result is FileDownloadResult.Progress) {
                val total = if (result.total == 0L) {
                    1L
                } else {
                    result.total
                }

                val percents = (result.downloaded.toFloat() / total.toFloat()) * 100f
                val downloadedString = PostUtils.getReadableFileSize(result.downloaded)
                val totalString = PostUtils.getReadableFileSize(total)

                log("Progress (" +
                        "${downloadedString} (${result.downloaded} B) / ${totalString} (${total} B)," +
                        " ${percents}%) for request ${request}"
                )

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onProgress(result.downloaded, total)
                    }
                }

                return
            }

            if (result.isErrorOfAnyKind()) {
                synchronized(activeDownloads) {
                    activeDownloads[requestId]?.cancelableDownload?.cancel()
                }

                purgeOutput(request.url, request.output)
            }

            handleSuccessOrError(result, requestId, request)
        } catch (error: Throwable) {
            Logger.e(TAG, "An error in result handler", error)
        }
    }

    private fun handleSuccessOrError(
            result: FileDownloadResult,
            requestId: String,
            request: FileDownloadRequest
    ) {
        when (result) {
            // Success/Progress
            is FileDownloadResult.Success -> {
                val (downloaded, total) = synchronized(activeDownloads) {
                    val downloaded = activeDownloads[requestId]?.downloaded?.get()
                    val total = activeDownloads[requestId]?.total?.get()

                    Pair(downloaded, total)
                }

                if (downloaded == null || total == null) {
                    return
                }

                val downloadedString = PostUtils.getReadableFileSize(downloaded)
                val totalString = PostUtils.getReadableFileSize(total)

                log("Success (" +
                        "downloaded = ${downloadedString} ($downloaded B), " +
                        "total = ${totalString} ($total B), " +
                        "took ${result.time}ms" +
                        ") for request ${request}"
                )

                // Trigger cache trimmer after a file has been successfully downloaded
                cacheHandler.fileWasAdded(total)

                resultHandler(requestId, request) {
                    onSuccess(result.file)
                    onEnd()
                }
            }
            is FileDownloadResult.Progress -> {
                // Do nothing, already handled above
            }

            // Cancel
            is FileDownloadResult.Canceled -> {
                log("Request ${request} canceled")

                resultHandler(requestId, request) {
                    onCancel()
                    onEnd()
                }
            }

            // Errors
            is FileDownloadResult.NotFound -> {
                logError("File not found for request ${request}")

                resultHandler(requestId, request) {
                    onFail(IOException("File not found"))
                    onEnd()
                }
            }
            is FileDownloadResult.Exception -> {
                logError("Exception for request ${request}", result.throwable)

                resultHandler(requestId, request) {
                    onFail(IOException(result.throwable))
                    onEnd()
                }
            }
            is FileDownloadResult.BadOutputFileError -> {
                val exception = IOException("Bad output file: " +
                        "exists = ${result.exists}, " +
                        "isFile = ${result.isFile}, " +
                        "canWrite = ${result.canWrite}"
                )

                logError("Bad output file error for request ${request}", exception)

                resultHandler(requestId, request) {
                    onFail(exception)
                    onEnd()
                }
            }
            is FileDownloadResult.HttpCodeIOError -> {
                val exception = if (result.statusCode != 404) {
                    IOException("Bad response status code: ${result.statusCode}")
                } else {
                    NotFoundException()
                }

                logError("Http code error for request ${request}, status code = ${result.statusCode}")

                resultHandler(requestId, request) {
                    onFail(exception)
                    onEnd()
                }
            }
            is FileDownloadResult.NoResponseBodyError -> {
                val exception = IOException("No response body returned for request ${request}")
                logError("No response body returned for request ${request}")

                resultHandler(requestId, request) {
                    onFail(exception)
                    onEnd()
                }
            }
            is FileDownloadResult.CouldNotGetOutputStreamError -> {
                logError("CouldNotGetOutputStreamError(" +
                        "exists = ${result.exists}, " +
                        "isFile = ${result.isFile}, " +
                        "canWrite = ${result.canWrite}) for request ${request}")

                resultHandler(requestId, request) {
                    onFail(IOException("Could not get output stream"))
                    onEnd()
                }
            }
            is FileDownloadResult.CouldNotCreateOutputFile -> {
                logError("CouldNotCreateOutputFile for request ${request}, " +
                        "output file path = ${result.filePath}")

                resultHandler(requestId, request) {
                    onFail(IOException("Could not create output file"))
                    onEnd()
                }
            }
        }.exhaustive
    }

    private fun resultHandler(
            requestId: String,
            request: FileDownloadRequest,
            func: FileCacheListener.() -> Unit
    ) {
        request.cancelableDownload.forEachCallback {
            BackgroundUtils.runOnUiThread {
                func()
            }
        }

        request.cancelableDownload.clearCallbacks()
        synchronized(activeDownloads) { activeDownloads.remove(requestId) }
    }

    private fun handleFileDownload(
            requestId: String
    ): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()

        val request = synchronized(activeDownloads) { activeDownloads[requestId] }
        if (request == null || request.cancelableDownload.isCanceled()) {
            return Flowable.error(CancellationException(requestId))
        }

        val exists = fileManager.exists(request.output)
        val outputFile = if (!exists) {
            fileManager.create(request.output) as? RawFile
        } else {
            request.output
        }

        if (outputFile == null) {
            return Flowable.just(
                    FileDownloadResult.CouldNotCreateOutputFile(request.output.getFullPath())
            )
        }

        val isFile = fileManager.isFile(outputFile)
        val canWrite = fileManager.canWrite(outputFile)

        if (!isFile || !canWrite) {
            return Flowable.just(
                    FileDownloadResult.BadOutputFileError(exists, isFile, canWrite)
            )
        }

        return sendRequest(requestId)
                .concatMap { response -> handleResponse(outputFile, requestId, response) }
                // Retry on IO error mechanism
                .retry(MAX_RETRIES) { error ->
                    val retry = error !is CancellationException && error is IOException

                    if (retry) {
                        log("Retrying request with requestId ${requestId}, " +
                                "error = ${error.javaClass.simpleName}")
                    }

                    retry
                }
                .onErrorReturn { throwable ->
                    if (isCancellationError(throwable)) {
                        return@onErrorReturn FileDownloadResult.Canceled
                    }

                    return@onErrorReturn FileDownloadResult.Exception(throwable)
                }
    }

    private fun handleResponse(
            output: RawFile,
            requestId: String,
            response: Response
    ): Flowable<FileDownloadResult> {
        BackgroundUtils.ensureBackgroundThread()

        var cachedResponseBody: ResponseBody? = null
        var cachedSink: BufferedSink? = null
        var cachedBufferedSource: BufferedSource? = null
        val isBodyClosed = AtomicBoolean(false)
        val exception = AtomicBoolean(false)

        // Another important callback that will clean up all the resource upon this stream dispose
        val cleanupResourcesFunc = {
            BackgroundUtils.ensureBackgroundThread()

            if (isBodyClosed.compareAndSet(false, true)) {
                log("cleanupResourcesFunc called for requestId ${requestId}")

                response.closeQuietly()
                cachedResponseBody?.closeQuietly()
                cachedSink?.closeQuietly()
                cachedBufferedSource?.closeQuietly()
            }
        }

        return Flowable.create({ emitter ->
            BackgroundUtils.ensureBackgroundThread()
            val serializedEmitter = emitter.serialize()

            serializedEmitter.setCancellable {
                cleanupResourcesFunc()
            }

            try {
                if (!response.isSuccessful) {
                    serializedEmitter.onNext(
                            FileDownloadResult.HttpCodeIOError(response.code)
                    )
                    return@create
                }

                if (isRequestCanceled(requestId)) {
                    throw CancellationException(requestId)
                }

                val responseBody = response.body.also { cachedResponseBody = it }
                if (responseBody == null) {
                    serializedEmitter.onNext(FileDownloadResult.NoResponseBodyError)
                    return@create
                }

                if (isRequestCanceled(requestId)) {
                    throw CancellationException(requestId)
                }

                val sink = try {
                    fileManager.getOutputStream(output)
                            ?.sink()
                            ?.buffer()
                            ?.also { cachedSink = it }
                } catch (error: Throwable) {
                    logError("Unknown error while trying to get an OutputStream", error)
                    null
                }

                if (isRequestCanceled(requestId)) {
                    throw CancellationException(requestId)
                }

                if (sink == null) {
                    val fileExists = fileManager.exists(output)
                    val isFile = fileManager.exists(output)
                    val canWrite = fileManager.exists(output)

                    val result = FileDownloadResult.CouldNotGetOutputStreamError(
                            fileExists,
                            isFile,
                            canWrite
                    )

                    serializedEmitter.onNext(result)
                    return@create
                }

                val source = responseBody.source().also { cachedBufferedSource = it }

                if (isRequestCanceled(requestId)) {
                    throw CancellationException(requestId)
                }

                val time = pipeBody(
                        serializedEmitter,
                        requestId,
                        responseBody.contentLength(),
                        source,
                        sink
                )

                markFileAsDownloaded(requestId)
                serializedEmitter.onNext(FileDownloadResult.Success(output, time))
            } catch (error: Throwable) {
                if (!exception.compareAndSet(false, true)) {
                    throw RuntimeException(
                            "serializedEmitter.onError() is called more than once!!!"
                    )
                }

                if (isCancellationError(error)) {
                    logError("CancellationException, " +
                            "exception = ${error.javaClass.name}, " +
                            "requestId = $requestId"
                    )

                    serializedEmitter.onError(CancellationException(requestId))
                } else {
                    serializedEmitter.onError(error)
                }
            } finally {
                if (!exception.get()) {
                    serializedEmitter.onComplete()
                }

                cleanupResourcesFunc()
            }
        }, BackpressureStrategy.DROP)
        // TODO: add scheduler
    }

    private fun markFileAsDownloaded(requestId: String) {
        val request = synchronized(activeDownloads) {
            checkNotNull(activeDownloads[requestId]) {
                "Active downloads does not have requestId: ${requestId} even though " +
                        "it was just downloaded"
            }
        }

        if (!cacheHandler.markFileDownloaded(request.output)) {
            throw CouldNotMarkFileAsDownloaded(request.output)
        }
    }

    private fun isCancellationError(error: Throwable): Boolean {
        if (error !is IOException) {
            return false
        }

        if (error is CancellationException
                || error is StreamResetException) {
            return true
        }

        if (error.message?.contains("Canceled") == true) {
            return true
        }

        return false
    }

    private fun isRequestCanceled(requestId: String): Boolean {
        BackgroundUtils.ensureBackgroundThread()

        return synchronized(activeDownloads) {
            if (!activeDownloads.containsKey(requestId)) {
                return@synchronized true
            }

            val request = checkNotNull(activeDownloads[requestId]) {
                "Request was removed inside a synchronized block. " +
                        "Apparently it's not thread-safe anymore"
            }

            return@synchronized request.cancelableDownload.isCanceled()
        }
    }

    private fun purgeOutput(requestId: String, output: RawFile) {
        BackgroundUtils.ensureBackgroundThread()

        synchronized(activeDownloads) {
            if (!activeDownloads.containsKey(requestId)) {
                return
            }

            val request = checkNotNull(activeDownloads[requestId]) {
                "Request was removed inside a synchronized block. " +
                        "Apparently it's not thread-safe anymore"
            }

            if (!request.cancelableDownload.isCanceled()) {
                // Not canceled
                return
            }
        }

        log("Purging $requestId, file = ${output.getFullPath()}")

        if (!cacheHandler.deleteCacheFile(output)) {
            logError("Could not delete the file in purgeOutput, output = ${output.getFullPath()}")
        }
    }

    private fun pipeBody(
            emitter: FlowableEmitter<FileDownloadResult>,
            requestId: String,
            contentLength: Long,
            source: Source,
            sink: BufferedSink
    ): Long {
        BackgroundUtils.ensureBackgroundThread()

        var read: Long
        var downloaded: Long = 0
        var notifyTotal: Long = 0
        val buffer = Buffer()
        val notifySize = contentLength / 10L
        val startTime = System.currentTimeMillis()

        while (true) {
            read = source.read(buffer, BUFFER_SIZE)
            if (read == -1L) {
                break
            }

            if (isRequestCanceled(requestId)) {
                throw CancellationException(requestId)
            }

            sink.write(buffer, read)
            downloaded += read

            if (downloaded >= notifyTotal + notifySize) {
                notifyTotal = downloaded

                val total = if (contentLength <= 0) {
                    downloaded
                } else {
                    contentLength
                }

                emitter.onNext(FileDownloadResult.Progress(downloaded, total))
            }
        }

        if (downloaded != contentLength) {
            throw CancellationException(requestId)
        }

        synchronized(activeDownloads) {
            activeDownloads[requestId]?.downloaded?.set(downloaded)
            activeDownloads[requestId]?.total?.set(contentLength)
        }

        emitter.onNext(FileDownloadResult.Progress(downloaded, contentLength))
        return System.currentTimeMillis() - startTime
    }

    private fun sendRequest(requestId: String): Flowable<Response> {
        BackgroundUtils.ensureBackgroundThread()

        if (isRequestCanceled(requestId)) {
            throw CancellationException(requestId)
        }

        val request = synchronized(activeDownloads) {
            checkNotNull(activeDownloads[requestId]) {
                "Request was removed inside a synchronized block. " +
                        "Apparently it's not thread-safe anymore"
            }
        }

        val httpRequest = Request.Builder()
                .url(request.url)
                .header("User-Agent", NetModule.USER_AGENT)
                .build()

        return Flowable.create<Response>({ emitter ->
            BackgroundUtils.ensureBackgroundThread()
            val call = okHttpClient.newCall(httpRequest)

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!isCancellationError(e)) {
                        emitter.onError(e)
                    } else {
                        emitter.onError(CancellationException(requestId))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    emitter.onNext(response)
                    emitter.onComplete()
                }
            })

            if (isRequestCanceled(requestId)) {
                emitter.onError(CancellationException(requestId))
                return@create
            }

            // A very important callback that will cancel the okHttp Call when we need to do it.
            // It's called from CancelableDownload.cancel()
            val disposeFunc = {
                BackgroundUtils.ensureBackgroundThread()

                if (!call.isCanceled()) {
                    log("Disposing OkHttp Call for request ${request} via manual canceling")
                    call.cancel()
                }
            }

            synchronized(activeDownloads) {
                activeDownloads[requestId]?.cancelableDownload?.disposeFunc = disposeFunc
            }

            emitter.setDisposable(object : Disposable {
                override fun isDisposed(): Boolean = call.isCanceled()

                override fun dispose() {
                    if (call.isCanceled()) {
                        return
                    }

                    log("Disposing OkHttp Call for request ${request} via emitter dispose")
                    call.cancel()
                }
            })
        }, BackpressureStrategy.BUFFER)
        // TODO: add scheduler
    }

    class FileDownloadRequest(
            val url: String,
            val output: RawFile,
            val downloaded: AtomicLong,
            val total: AtomicLong,
            val cancelableDownload: CancelableDownload
    ) {
        override fun toString(): String {
            return "[FileDownloadRequest: " +
                    "url = ${url}, " +
                    "outputFileName = ${File(output.getFullPath()).name}]"
        }
    }

    class CancellationException(requestId: String)
        : IOException("CancellationException for request with id ${requestId}")

    class NotFoundException : Exception()

    class CouldNotMarkFileAsDownloaded(val output: RawFile)
        : Exception("Couldn't mark file as downloaded, file path = ${output.getFullPath()}")

    sealed class FileDownloadResult {
        class Success(val file: RawFile, val time: Long) : FileDownloadResult()
        class Progress(val downloaded: Long, val total: Long) : FileDownloadResult()

        // Errors
        object NotFound : FileDownloadResult()

        object Canceled : FileDownloadResult()
        class Exception(val throwable: Throwable) : FileDownloadResult()
        class CouldNotCreateOutputFile(val filePath: String) : FileDownloadResult()
        class BadOutputFileError(val exists: Boolean, val isFile: Boolean, val canWrite: Boolean) : FileDownloadResult()
        class CouldNotGetOutputStreamError(val exists: Boolean, val isFile: Boolean, val canWrite: Boolean) : FileDownloadResult()
        class HttpCodeIOError(val statusCode: Int) : FileDownloadResult()
        object NoResponseBodyError : FileDownloadResult()

        fun isErrorOfAnyKind(): Boolean {
            return this !is Success && this !is Progress
        }
    }

    inner class CancelableDownload(
            val url: String,
            private val canceled: AtomicBoolean = AtomicBoolean(false),
            private val isPartOfBatchDownload: AtomicBoolean = AtomicBoolean(false),
            private var callbacks: MutableSet<FileCacheListener> = mutableSetOf(),
            var disposeFunc: (() -> Unit)? = null
    ) {
        fun isCanceled() = canceled.get()

        @Synchronized
        fun addCallback(callback: FileCacheListener) {
            if (canceled.get()) {
                return
            }

            callbacks.add(callback)
        }

        @Synchronized
        fun forEachCallback(func: FileCacheListener.() -> Unit) {
            callbacks.forEach { func(it) }
        }

        @Synchronized
        fun clearCallbacks() {
            callbacks.clear()
        }

        /**
         * Use this to cancel prefetches. You can't cancel them via the regular cancel() method
         * to avoid canceling prefetches when swiping through the images in the album viewer.
         * */
        fun cancelPrefetch() {
            cancel(true)
        }

        /**
         * A regular cancel() method that cancels active downloads but not prefetch downloads.
         * */
        fun cancel() {
            cancel(false)
        }

        private fun cancel(canCancelBatchDownloads: Boolean) {
            if (!canceled.compareAndSet(false, true)) {
                // Already canceled
                return
            }

            if (isPartOfBatchDownload.get() && !canCancelBatchDownloads) {
                // When prefetching media in a thread and viewing images in the same thread at the
                // same time we may accidentally cancel a prefetch download which we don't want.
                // We only want to cancel prefetch downloads when exiting a thread not when swiping
                // through the images in the album viewer.
                return
            }

            // We need to cancel the network requests on a background thread.
            // We also want it to be blocking.
            requestCancellationThread.submit {
                disposeFunc?.invoke()
                disposeFunc = null

                log("Cancelling file download request, requestId = $url")
            }
                    // Just in case
                    .get(5, TimeUnit.SECONDS)
        }
    }

    companion object {
        private const val TAG = "FileCacheV2"
        private const val NORMAL_THREAD_NAME_FORMAT = "NormalFileCacheV2Thread-%d"
        private const val BATCH_THREAD_NAME_FORMAT = "BatchFileCacheV2Thread-%d"
        private const val BUFFER_SIZE: Long = 8192L
        private const val MAX_RETRIES = 5L

        private fun log(message: String) {
            Logger.d(TAG, String.format("[%s]: %s", Thread.currentThread().name, message))
        }

        private fun logError(message: String, error: Throwable? = null) {
            if (error == null) {
                Logger.e(TAG, String.format("[%s]: %s", Thread.currentThread().name, message))
            } else {
                Logger.e(TAG, String.format("[%s]: %s", Thread.currentThread().name, message), error)
            }
        }
    }
}