package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.AbstractFile
import io.reactivex.exceptions.CompositeException

internal sealed class FileCacheException(message: String) : Exception(message) {

    internal class CancellationException(val state: DownloadState, url: String)
        : FileCacheException("CancellationException for request with " +
            "url = ${url}, state = ${state.javaClass.simpleName}")

    internal class FileNotFoundOnTheServerException
        : FileCacheException("FileNotFoundOnTheServerException")

    internal class CouldNotMarkFileAsDownloaded(val output: AbstractFile)
        : FileCacheException("Couldn't mark file as downloaded, file path = ${output.getFullPath()}")

    internal class NoResponseBodyException
        : FileCacheException("NoResponseBodyException")

    internal class CouldNotCreateOutputFileException(val filePath: String)
        : FileCacheException("Could not create output file, path = ${filePath}")

    internal class CouldNotGetInputStreamException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            val canRead: Boolean
    ) : FileCacheException("Could not get input stream, exists = $exists, isFile = $isFile, canRead = $canRead, path = $path")

    internal class CouldNotGetOutputStreamException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            val canWrite: Boolean
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
            val canWrite: Boolean
    ) : FileCacheException("Bad output file, exists = $exists, isFile = $isFile, canWrite = $canWrite, path = $path")

    internal class FileHashesAreDifferent(
            val url: String,
            val fileName: String,
            val expectedFileHash: String,
            val actualFileHash: String
    ) : FileCacheException("Downloaded file's hash differs from the one we got from the server!" +
            "\nSomething is wrong with the file, use force reload!" +
            "\nexpected = \"$expectedFileHash\", actual = \"$actualFileHash\", url = ${url}, file name = ${fileName}")
}

internal fun logErrorsAndExtractErrorMessage(tag: String, prefix: String, error: Throwable): String {
    return if (error is CompositeException) {
        val sb = StringBuilder()

        for ((index, exception) in error.exceptions.withIndex()) {
            sb.append(
                    "$prefix ($index), " +
                            "class = ${exception.javaClass.simpleName}, " +
                            "message = ${exception.message}"
            ).append(";\n")
        }

        val result = sb.toString()
        logError(tag, result)

        result
    } else {
        val msg = "$prefix, class = ${error.javaClass.simpleName}, message = ${error.message}"
        logError(tag, msg)

        msg
    }
}