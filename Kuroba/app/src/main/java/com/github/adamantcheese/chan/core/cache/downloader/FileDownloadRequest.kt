package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal class FileDownloadRequest(
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