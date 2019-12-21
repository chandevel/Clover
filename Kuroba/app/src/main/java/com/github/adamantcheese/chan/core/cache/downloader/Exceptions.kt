package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile

internal sealed class FileCacheException(message: String) : Exception(message) {
    internal class CancellationException(val state: DownloadState, url: String)
        : FileCacheException("CancellationException for request with " +
            "url = ${url}, state = ${state.javaClass.name}")

    internal class StoppedException(val state: DownloadState, url: String)
        : FileCacheException("StoppedException for request with " +
            "url = ${url}, state = ${state.javaClass.name}")

    internal class FileNotFoundOnTheServerException
        : FileCacheException("FileNotFoundOnTheServerException")

    internal class CouldNotMarkFileAsDownloaded(val output: RawFile)
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

    // TODO: this should be probably thrown when HEAD request returns back non 200 response.
    //  Needs testing
    internal class HttpCodeException(val statusCode: Int)
        : FileCacheException("HttpCodeException statusCode = $statusCode")

    internal class BadOutputFileException(
            val path: String,
            val exists: Boolean,
            val isFile: Boolean,
            val canWrite: Boolean
    ) : FileCacheException("Bad output file, exists = $exists, isFile = $isFile, canWrite = $canWrite, path = $path")
}