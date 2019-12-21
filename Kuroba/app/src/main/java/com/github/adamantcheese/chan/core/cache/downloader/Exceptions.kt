package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile
import java.io.IOException

internal class CancellationException(val state: DownloadState, url: String)
    : IOException("CancellationException for request with " +
        "url = ${url}, state = ${state.javaClass.name}")

internal class FileNotFoundOnTheServerException : Exception()

internal class CouldNotMarkFileAsDownloaded(val output: RawFile)
    : Exception("Couldn't mark file as downloaded, file path = ${output.getFullPath()}")

internal class NoResponseBodyException : Exception()

internal class CouldNotGetInputStreamException(
        val path: String,
        val exists: Boolean,
        val isFile: Boolean,
        val canRead: Boolean
) : Exception()

internal class CouldNotGetOutputStreamException(
        val path: String,
        val exists: Boolean,
        val isFile: Boolean,
        val canWrite: Boolean
) : Exception()

internal class OutputFileDoesNotExist(val path: String) : Exception()
internal class ChunkFileDoesNotExist(val path: String) : Exception()