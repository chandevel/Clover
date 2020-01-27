package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile

internal sealed class FileDownloadResult {
    class Start(val chunksCount: Int) : FileDownloadResult()
    class Success(val file: RawFile, val requestTime: Long) : FileDownloadResult()
    class Progress(val chunkIndex: Int, val downloaded: Long, val chunkSize: Long) : FileDownloadResult()
    object Canceled : FileDownloadResult()
    object Stopped : FileDownloadResult()
    class KnownException(val fileCacheException: FileCacheException) : FileDownloadResult()
    class UnknownException(val error: Throwable) : FileDownloadResult()

    fun isErrorOfAnyKind(): Boolean {
        return this !is Start && this !is Success && this !is Progress
    }
}