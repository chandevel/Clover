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
import java.net.SocketException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A file downloader with two queues:
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
    private val requestQueue = PublishProcessor.create<String>()

    // TODO: implement the second queue
    private val batchRequestQueue = PublishProcessor.create<String>()

    private val threadsCount = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(3)
    private val threadIndex = AtomicInteger(0)
    private val requestCancellationThread = Executors.newSingleThreadExecutor()
    private val workerScheduler = Schedulers.from(
            Executors.newFixedThreadPool(threadsCount) { runnable ->
                return@newFixedThreadPool Thread(
                        runnable,
                        String.format(THREAD_NAME_FORMAT, threadIndex.getAndIncrement())
                )
            }
    )

    init {
        initRxWorkerQueue()
    }

    // TODO: make 2 queues, one for one-by-one image file downloading and the other one for
    //  batch downloading

    @SuppressLint("CheckResult")
    private fun initRxWorkerQueue() {
        requestQueue
                .observeOn(workerScheduler)
                .onBackpressureBuffer()
                .flatMap { requestId ->
                    return@flatMap Flowable.defer { handleNormalFileDownload(requestId) }
                            .subscribeOn(workerScheduler)
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

    fun enqueueDownloadFileRequest(
            loadable: Loadable,
            postImage: PostImage,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val url = postImage.imageUrl.toString()

        if (loadable.isLocal) {
            log("Handling local thread file, url = $url")

            if (callback == null) {
                logError("Callback is null for a local thread")
                return null
            }

            return handleLoadThreadFile(loadable, postImage, callback)
        }

        return enqueueDownloadFileRequest(url, callback)
    }

    fun enqueueDownloadFileRequest(
            url: String,
            callback: FileCacheListener?
    ): CancelableDownload? {
        val file: RawFile = cacheHandler.get(url)

        val cancelableDownload = synchronized(activeDownloads) {
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

                return prevCancelableDownload
            }

            val cancelableDownload = CancelableDownload(url = url)
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

        if (fileManager.exists(file)) {
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

            return null
        }

        log("Downloading a file, url = $url")
        requestQueue.onNext(url)

        return cancelableDownload
    }

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

    private fun handleFileImmediatelyAvailable(file: AbstractFile, requestId: String) {
        val request = synchronized(activeDownloads) { activeDownloads[requestId] }
                ?: return

        if (file is RawFile) {
            request.cancelableDownload.forEachCallback {
                BackgroundUtils.runOnUiThread {
                    onSuccess(file)
                    onEnd()
                }
            }
        } else {
            try {
                val resultFile = fileManager.fromRawFile(cacheHandler.randomCacheFile())
                if (!fileManager.copyFileContents(file, resultFile)) {
                    throw IOException("Could not copy external SAF file into internal cache file, " +
                            "externalFile = " + file.getFullPath() +
                            ", resultFile = " + resultFile.getFullPath())
                }

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onSuccess(resultFile)
                        onEnd()
                    }
                }
            } catch (e: IOException) {
                logError("Error while trying to create a new random cache file", e)
                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(e)
                        onEnd()
                    }
                }
            }
        }
    }

    private fun handleFileImmediatelyAvailable(file: AbstractFile, callback: FileCacheListener?) {
        if (file is RawFile) {
            BackgroundUtils.runOnUiThread {
                callback?.onSuccess(file)
                callback?.onEnd()
            }
        } else {
            try {
                val resultFile = fileManager.fromRawFile(cacheHandler.randomCacheFile())
                if (!fileManager.copyFileContents(file, resultFile)) {
                    throw IOException("Could not copy external SAF file into internal cache file, " +
                            "externalFile = " + file.getFullPath() +
                            ", resultFile = " + resultFile.getFullPath())
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

        val request = synchronized(activeDownloads) { activeDownloads[requestId] }
                // Nothing to do
                ?: return

        if (result.isErrorOfAnyKind()) {
            synchronized(activeDownloads) {
                activeDownloads[requestId]?.cancelableDownload?.cancel()
            }

            purgeOutput(request.url, request.output)
        }

        if (result is FileDownloadResult.Canceled) {
            log("Request ${request} canceled")

            request.cancelableDownload.forEachCallback {
                BackgroundUtils.runOnUiThread {
                    onCancel()
                    onEnd()
                }
            }

            request.cancelableDownload.clearCallbacks()
            synchronized(activeDownloads) { activeDownloads.remove(requestId) }

            return
        }

        when (result) {
            is FileDownloadResult.Success -> {
                val (downloaded, total) = synchronized(activeDownloads) {
                    val downloaded = activeDownloads[requestId]?.downloaded?.get()
                    val total = activeDownloads[requestId]?.total?.get()

                    Pair(downloaded, total)
                }

                log("Success (downloaded = $downloaded, total = $total, took ${result.time}ms)" +
                        " for request ${request}")
                cacheHandler.fileWasAdded(fileManager.getLength(result.file))

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onSuccess(result.file)
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.Progress -> {
                val total = if (result.total == 0L) {
                    1L
                } else {
                    result.total
                }

                val percents = (result.downloaded.toFloat() / total.toFloat()) * 100f
                log("Progress (${result.downloaded}B / ${total}B, $percents%) for request ${request}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onProgress(result.downloaded, total)
                    }
                }

                Unit
            }

            // Errors
            is FileDownloadResult.Canceled -> {
                // Do nothing
                Unit
            }
            is FileDownloadResult.NotFound -> {
                logError("File not found for request ${request}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(IOException("File not found"))
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.Exception -> {
                logError("Exception for request ${request}", result.throwable)

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(IOException(result.throwable))
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.BadOutputFileError -> {
                val exception = IOException("Bad output file: " +
                        "exists = ${result.exists}, " +
                        "isFile = ${result.isFile}, " +
                        "canWrite = ${result.canWrite}"
                )

                logError("Bad output file error for request ${request}", exception)

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(exception)
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.HttpCodeIOError -> {
                val exception = if (result.statusCode != 404) {
                    IOException("Bad response status code: ${result.statusCode}")
                } else {
                    NotFoundException()
                }

                logError("Http code error for request ${request}, status code = ${result.statusCode}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(exception)
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.NoResponseBodyError -> {
                val exception = IOException("No response body returned for request ${request}")
                logError("No response body returned for request ${request}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(exception)
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.CouldNotGetOutputStreamError -> {
                logError("CouldNotGetOutputStreamError(" +
                        "exists = ${result.exists}, " +
                        "isFile = ${result.isFile}, " +
                        "canWrite = ${result.canWrite}) for request ${request}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(IOException("Could not get output stream"))
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
            is FileDownloadResult.CouldNotCreateOutputFile -> {
                logError("CouldNotCreateOutputFile for request ${request}, " +
                        "output file path = ${result.filePath}")

                request.cancelableDownload.forEachCallback {
                    BackgroundUtils.runOnUiThread {
                        onFail(IOException("Could not create output file"))
                        onEnd()
                    }
                }

                request.cancelableDownload.clearCallbacks()
                synchronized(activeDownloads) { activeDownloads.remove(requestId) }

                Unit
            }
        }.exhaustive
    }

    private fun handleNormalFileDownload(
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
                .concatMap { response ->
                    return@concatMap handleResponse(outputFile, requestId, response)
                            .unsubscribeOn(workerScheduler)
                }
                .retry(MAX_RETRIES) { error ->
                    val retry = error !is CancellationException && error is IOException

                    if (retry) {
                        log("Retrying request with requestId ${requestId}")
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
        var cachedResponseBody: ResponseBody? = null
        var cachedSink: BufferedSink? = null
        var cachedBufferedSource: BufferedSource? = null
        val isBodyClosed = AtomicBoolean(false)
        val exception = AtomicBoolean(false)

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
    }

    private fun isCancellationError(error: Throwable): Boolean {
        if (error !is IOException) {
            return false
        }

        if (error is CancellationException
                || error is StreamResetException
                || error is SocketException) {
            return true
        }

        if (error.message?.contains("Canceled") == true) {
            return true
        }

        return false
    }

    private fun isRequestCanceled(requestId: String): Boolean {
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

        if (fileManager.exists(output)) {
            log("Purging $requestId, file = ${output.getFullPath()}")
            val deleteResult = fileManager.delete(output)

            if (!deleteResult) {
                log("Could not delete the file in purgeOutput")
            }
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
            handleFileImmediatelyAvailable(localImgFile, callback)
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

        fun cancel() {
            if (!canceled.compareAndSet(false, true)) {
                // Already canceled
                return
            }

            // We need to cancel the network requests on a background thread
            requestCancellationThread.execute {
                disposeFunc?.invoke()
                disposeFunc = null

                log("Cancelling file download request, requestId = $url")
            }
        }
    }

    companion object {
        private const val TAG = "FileCacheV2"
        private const val THREAD_NAME_FORMAT = "FileCacheV2Thread-%d"
        private const val BUFFER_SIZE: Long = 8192
        private const val MAX_RETRIES = 3L

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