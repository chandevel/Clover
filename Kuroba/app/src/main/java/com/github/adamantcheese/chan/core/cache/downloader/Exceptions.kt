package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import com.github.k1rakishou.fsaf.file.AbstractFile
import io.reactivex.exceptions.CompositeException
import okhttp3.HttpUrl

internal sealed class FileCacheException(message: String) : Exception(message) {

    internal class CancellationException(val state: DownloadState, url: HttpUrl)
        : FileCacheException("CancellationException for request with " +
            "url = ${maskImageUrl(url)}, state = ${state.javaClass.simpleName}")

    internal class FileNotFoundOnTheServerException
        : FileCacheException("FileNotFoundOnTheServerException")

    internal class CouldNotMarkFileAsDownloaded(val output: AbstractFile)
        : FileCacheException("Couldn't mark file as downloaded, file path = ${output.getFullPath()}")

    internal class NoResponseBodyException
        : FileCacheException("NoResponseBodyException")

    internal class CouldNotCreateOutputFileException(filePath: String)
        : FileCacheException("Could not create output file, path = $filePath")

    internal class CouldNotGetInputStreamException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            canRead: Boolean
    ) : FileCacheException("Could not get input stream, exists = $exists, isFile = $isFile, canRead = $canRead, path = $path")

    internal class CouldNotGetOutputStreamException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            canWrite: Boolean
    ) : FileCacheException("Could not get output stream, exists = $exists, isFile = $isFile, canWrite = $canWrite, path = $path")

    internal class OutputFileDoesNotExist(val path: String)
        : FileCacheException("OutputFileDoesNotExist path = $path")

    internal class ChunkFileDoesNotExist(val path: String)
        : FileCacheException("ChunkFileDoesNotExist path = $path")

    internal class HttpCodeException(val statusCode: Int)
        : FileCacheException("HttpCodeException statusCode = $statusCode")

    internal class BadOutputFileException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            canWrite: Boolean
    ) : FileCacheException("Bad output file, exists = $exists, isFile = $isFile, canWrite = $canWrite, path = $path")
}

internal fun logErrorsAndExtractErrorMessage(tag: String, prefix: String, error: Throwable): String {
    return if (error is CompositeException) {
        val sb = StringBuilder()
        var verboseLog = false

        for ((index, exception) in error.exceptions.withIndex()) {
            sb.append(
                    "$prefix ($index), " +
                            "class = ${exception.javaClass.simpleName}, " +
                            "message = ${exception.message}"
            ).append(";\n")
            verboseLog = verboseLog || exception is FileCacheException.CancellationException
        }

        val result = sb.toString()
        if(!verboseLog) {
            Logger.e(tag, result)
        } else {
            Logger.ve(tag, result)
        }

        result
    } else {
        val msg = "$prefix, class = ${error.javaClass.simpleName}, message = ${error.message}"
        if(error is FileCacheException.CancellationException) {
            Logger.ve(tag, msg)
        } else {
            Logger.e(tag, msg)
        }

        msg
    }
}