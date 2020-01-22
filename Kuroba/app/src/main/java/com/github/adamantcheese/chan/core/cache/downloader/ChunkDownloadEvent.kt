package com.github.adamantcheese.chan.core.cache.downloader

import com.github.k1rakishou.fsaf.file.RawFile

internal sealed class ChunkDownloadEvent {
    class Success(val output: RawFile, val requestTime: Long) : ChunkDownloadEvent()
    class ChunkSuccess(val chunkIndex: Int, val chunkCacheFile: RawFile, val chunk: Chunk) : ChunkDownloadEvent()
    class ChunkError(val error: Throwable) : ChunkDownloadEvent()
    class Progress(val chunkIndex: Int, val downloaded: Long, val chunkSize: Long) : ChunkDownloadEvent()
}