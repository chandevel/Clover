package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile

internal sealed class FileDownloadResult {

    sealed class Success : FileDownloadResult() {
        data class NormalSuccess(val file: RawFile, val requestTime: Long) : Success()
        data class ChunkSuccess(val chunkFile: RawFile, val chunkFileOffset: Long) : Success()
    }

    sealed class Progress : FileDownloadResult() {
        data class NormalProgress(val downloaded: Long, val total: Long) : Progress()
        data class ChunkProgress(val downloaderIndex: Int, val downloaded: Long, val total: Long) : Progress()
    }

    // Errors
    class Exception(val throwable: Throwable) : FileDownloadResult()

    // TODO: replace all of the following errors with exceptions vvv
    object NotFound : FileDownloadResult()
    object Canceled : FileDownloadResult()
    object Stopped : FileDownloadResult()
    object DoesNotSupportPartialContent : FileDownloadResult()
    data class CouldNotCreateOutputFile(val filePath: String) : FileDownloadResult()
    data class BadOutputFileError(val exists: Boolean, val isFile: Boolean, val canWrite: Boolean) : FileDownloadResult()
    data class CouldNotGetInputStreamError(val path: String, val exists: Boolean, val isFile: Boolean, val canWrite: Boolean) : FileDownloadResult()
    data class CouldNotGetOutputStreamError(val path: String, val exists: Boolean, val isFile: Boolean, val canWrite: Boolean) : FileDownloadResult()
    data class HttpCodeIOError(val statusCode: Int) : FileDownloadResult()
    object NoResponseBodyError : FileDownloadResult()
    // TODO ^^^

    fun isErrorOfAnyKind(): Boolean {
        return this !is Success && this !is Progress
    }
}