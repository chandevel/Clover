package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal class FileDownloadRequest(
        val url: String,
        val output: RawFile,
        // A file will be split into [chunksCount] chunks which will be downloaded in parallel.
        // Must be 1 or greater than 1.
        val chunksCount: Int,
        // How many bytes were downloaded across all chunks
        val downloaded: AtomicLong,
        // How many bytes a file we download takes in total
        val total: AtomicLong,
        // A handle to cancel the current download
        val cancelableDownload: CancelableDownload,
        val chunks: MutableSet<Chunk> = mutableSetOf()
) {

    init {
        check(chunksCount >= 1) {
            "chunksCount is zero or less than zero! chunksCount = $chunksCount"
        }
    }

    override fun toString(): String {
        return "[FileDownloadRequest: " +
                "url = ${url}, " +
                "outputFileName = ${File(output.getFullPath()).name}]"
    }
}