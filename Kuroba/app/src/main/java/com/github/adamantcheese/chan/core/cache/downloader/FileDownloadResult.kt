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
    object Canceled : FileDownloadResult()
    object Stopped : FileDownloadResult()
    class KnownException(val fileCacheException: FileCacheException) : FileDownloadResult()
    class UnknownException(val error: Throwable) : FileDownloadResult()

    fun isErrorOfAnyKind(): Boolean {
        return this !is Success && this !is Progress
    }
}