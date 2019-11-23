/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.cache

import android.os.Handler
import android.os.Looper
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.github.adamantcheese.chan.Chan
import com.github.adamantcheese.chan.core.di.NetModule
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.closeQuietly
import okio.*
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FileCacheDownloader(
        private val fileManager: FileManager,
        // Main thread only.
        private val callback: Callback?,
        private val output: RawFile,
        val url: String
) : Runnable {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<FileCacheListener>()

    // Main and worker thread.
    private val running = AtomicBoolean(false)
    private val cancel = AtomicBoolean(false)

    // When encountering an IOException during the attempt to download a file instead of immediately
    // terminating the downloading we will try to download it couple of more times (3 times max).
    // If we are still unable to download the file after all of the attempts (no internet connection)
    // we give up and throw the latest exception. This should fix the annoying "Saved image failed"
    // error when downloading thread albums.
    //
    // The reason is actually SocketTimeoutException is the server forcefully drops
    // the connection if it detects that we are downloading files?
    private val retryAttempts = AtomicInteger(3)
    private val retryTimeoutsSeconds = arrayOf(5L, 2L, 1L)

    @MainThread
    fun addListener(callback: FileCacheListener) {
        BackgroundUtils.ensureMainThread()

        listeners.add(callback)
    }

    /**
     * Cancel this download.
     */
    @MainThread
    fun cancel() {
        BackgroundUtils.ensureMainThread()

        if (cancel.compareAndSet(false, true)) {
            // Did not start running yet, mark finished here.
            if (!running.get() && callback != null) {
                callback.downloaderFinished(this)
            }
        }
    }

    @AnyThread
    private fun log(message: String) {
        Logger.d(TAG, logPrefix() + message)
    }

    @AnyThread
    private fun log(message: String, e: Exception) {
        Logger.e(TAG, logPrefix() + message, e)
    }

    private fun logPrefix(): String {
        return "[" + url.substring(0, url.length.coerceAtMost(45)) + "] "
    }

    @WorkerThread
    override fun run() {
        BackgroundUtils.ensureBackgroundThread()

        log("start")
        running.set(true)
        execute()
    }

    @WorkerThread
    private fun execute() {
        var sourceCloseable: Closeable? = null
        var sinkCloseable: Closeable? = null
        var outputFileOutputStream: OutputStream? = null
        var call: Call? = null
        var body: ResponseBody? = null

        try {
            BackgroundUtils.ensureBackgroundThread()
            checkCancel()

            val timeout = retryTimeoutsSeconds.getOrElse(retryAttempts.get()) { 0 }
            if (timeout > 0) {
                try {
                    log("Sleeping for $timeout seconds")
                    Thread.sleep(timeout * 1000L)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            checkCancel()

            val startTime = System.currentTimeMillis()
            val (newCall, newBody) = getBody()
            if (newBody == null) {
                throw IOException("body == null")
            }

            call = newCall
            body = newBody

            val source = body.source().also { source ->
                sourceCloseable = source
            }

            if (!fileManager.exists(output) && fileManager.create(output) == null) {
                throw IOException("Couldn't create output file, output = " + output.getFullPath())
            }

            outputFileOutputStream = fileManager.getOutputStream(output)
            if (outputFileOutputStream == null) {
                throw IOException("Couldn't get output file's OutputStream")
            }

            val sink = outputFileOutputStream.sink().buffer().also { sink ->
                sinkCloseable = sink
            }

            checkCancel()

            log("got input stream")
            pipeBody(body, source, sink)

            val deltaTime = System.currentTimeMillis() - startTime
            val fileLength = fileManager.getLength(output)
            val fileSizeInKB = fileLength / 1024

            log("File with size $fileSizeInKB KB was downloaded done in $deltaTime ms")

            postSuccessResult(
                    callback,
                    fileLength
            )

        } catch (e: IOException) {
            BackgroundUtils.ensureBackgroundThread()

            var isNotFound = false
            var canceled = false

            when (e) {
                is HttpCodeIOException -> {
                    val code = e.code
                    log("exception: http error, code: $code", e)
                    isNotFound = code == 404
                }
                is CancelException -> {
                    // Don't log the stack.
                    log("exception: canceled")
                    canceled = true
                }
                else -> {
                    if (retryRequest()) {
                        log("An IOException (" + e.javaClass.simpleName +
                                ") has occurred, there are " + retryAttempts.get() +
                                " retry attempts, running the request again")

                        execute()
                    } else {
                        log("No more retry attempts left, throwing the exception", e)
                        postNetworkError(e)
                    }
                }
            }

            val finalIsNotFound = isNotFound
            val finalCanceled = canceled

            postErrorResult(finalCanceled, finalIsNotFound)
        } finally {
            BackgroundUtils.ensureBackgroundThread()

            sourceCloseable?.closeQuietly()
            sinkCloseable?.closeQuietly()
            outputFileOutputStream?.closeQuietly()
            call?.cancel()
            body?.closeQuietly()
        }
    }

    private fun retryRequest(): Boolean {
        BackgroundUtils.ensureBackgroundThread()
        return retryAttempts.getAndDecrement() > 0
    }

    private fun postNetworkError(error: IOException) {
        BackgroundUtils.ensureBackgroundThread()

        handler.post {
            for (callback in listeners) {
                callback.onNetworkError(error)
            }
        }
    }

    private fun postErrorResult(finalCanceled: Boolean, finalIsNotFound: Boolean) {
        BackgroundUtils.ensureBackgroundThread()
        purgeOutput()

        handler.post {
            for (callback in listeners) {
                if (finalCanceled) {
                    callback.onCancel()
                } else {
                    callback.onFail(finalIsNotFound)
                }

                callback.onEnd()
            }

            callback?.downloaderFinished(this)
        }
    }

    private fun postSuccessResult(callback: Callback?, fileLen: Long) {
        BackgroundUtils.ensureBackgroundThread()

        handler.post {
            if (callback != null) {
                callback.downloaderAddedFile(fileLen)
                callback.downloaderFinished(this)
            }

            listeners.forEach { cb ->
                cb.onSuccess(output)
                cb.onEnd()
            }
        }
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun getBody(): Pair<Call, ResponseBody?> {
        BackgroundUtils.ensureBackgroundThread()

        val request = Request.Builder()
                .url(url)
                .header("User-Agent", NetModule.USER_AGENT)
                .build()

        //we want to use the proxy instance here
        val call = (Chan.injector().instance(OkHttpClient::class.java) as NetModule.ProxiedOkHttpClient)
                .proxiedClient.newCall(request)

        val response = call.execute()
        if (!response.isSuccessful) {
            throw HttpCodeIOException(response.code)
        }

        checkCancel()
        return Pair(call, response.body)
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun pipeBody(body: ResponseBody?, source: Source, sink: BufferedSink) {
        BackgroundUtils.ensureBackgroundThread()

        if (body == null) {
            throw IOException("pipeBody() body == null")
        }

        val contentLength = body.contentLength()

        var read: Long
        var total: Long = 0
        var notifyTotal: Long = 0
        val buffer = Buffer()

        while (true) {
            read = source.read(buffer, BUFFER_SIZE)
            if (read == -1L) {
                break
            }

            sink.write(buffer, read)
            total += read

            if (total >= notifyTotal + NOTIFY_SIZE) {
                notifyTotal = total
                log("progress " + total / contentLength.toFloat())
                postProgress(total, if (contentLength <= 0) total else contentLength)
            }

            checkCancel()
        }

        source.closeQuietly()
        sink.closeQuietly()
        body.closeQuietly()
    }

    @WorkerThread
    @Throws(IOException::class)
    private fun checkCancel() {
        BackgroundUtils.ensureBackgroundThread()

        if (cancel.get()) {
            throw CancelException()
        }
    }

    @WorkerThread
    private fun purgeOutput() {
        BackgroundUtils.ensureBackgroundThread()

        if (fileManager.exists(output)) {
            val deleteResult = fileManager.delete(output)

            if (!deleteResult) {
                log("could not delete the file in purgeOutput")
            }
        }
    }

    @WorkerThread
    private fun postProgress(downloaded: Long, total: Long) {
        BackgroundUtils.ensureBackgroundThread()

        handler.post {
            for (callback in listeners) {
                callback.onProgress(downloaded, total)
            }
        }
    }

    private class CancelException : IOException()
    private class HttpCodeIOException(val code: Int) : IOException()

    interface Callback {
        fun downloaderFinished(fileCacheDownloader: FileCacheDownloader)
        fun downloaderAddedFile(fileLen: Long)
    }

    companion object {
        private const val TAG = "FileCacheDownloader"
        private const val BUFFER_SIZE: Long = 8192
        private const val NOTIFY_SIZE = BUFFER_SIZE * 8
    }
}
