package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.site.SiteResolver
import com.github.adamantcheese.chan.utils.Logger
import com.github.adamantcheese.chan.utils.StringUtils.maskImageUrl
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.Flowable
import okhttp3.HttpUrl


internal class ChunkMerger(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val siteResolver: SiteResolver,
        private val activeDownloads: ActiveDownloads
) {

    fun mergeChunksIntoCacheFile(
            url: HttpUrl,
            chunkSuccessEvents: List<ChunkDownloadEvent.ChunkSuccess>,
            output: RawFile,
            requestStartTime: Long
    ): Flowable<ChunkDownloadEvent> {
        return Flowable.fromCallable {
            Logger.vd(this, "mergeChunksIntoCacheFile called (${maskImageUrl(url)}), " +
                    "chunks count = ${chunkSuccessEvents.size}")

            val isRunning = activeDownloads.get(url)?.cancelableDownload?.isRunning() ?: false
            if (!isRunning) {
                activeDownloads.throwCancellationException(url)
            }

            try {
                // Must be sorted in ascending order!!!
                val sortedChunkEvents = chunkSuccessEvents.sortedBy { event -> event.chunk.start }

                if (!fileManager.exists(output)) {
                    throw FileCacheException.OutputFileDoesNotExist(output.getFullPath())
                }

                fileManager.getOutputStream(output)?.use { outputStream ->
                    // Iterate each chunk and write it to the output file
                    for (chunkEvent in sortedChunkEvents) {
                        val chunkFile = chunkEvent.chunkCacheFile

                        if (!fileManager.exists(chunkFile)) {
                            throw FileCacheException.ChunkFileDoesNotExist(chunkFile.getFullPath())
                        }

                        fileManager.getInputStream(chunkFile)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        } ?: throw FileCacheException.CouldNotGetInputStreamException(
                                chunkFile.getFullPath(),
                                true,
                                fileManager.isFile(chunkFile),
                                fileManager.canRead(chunkFile)
                        )
                    }

                    outputStream.flush()
                } ?: throw FileCacheException.CouldNotGetOutputStreamException(
                        output.getFullPath(),
                        true,
                        fileManager.isFile(output),
                        fileManager.canRead(output)
                )
            } finally {
                // In case of success or an error we want delete all chunk files
                chunkSuccessEvents.forEach { event ->
                    if (!fileManager.delete(event.chunkCacheFile)) {
                        Logger.e(this, "Couldn't delete chunk file: ${event.chunkCacheFile.getFullPath()}")
                    }
                }
            }

            // Mark file as downloaded
            markFileAsDownloaded(url)

            val requestTime = System.currentTimeMillis() - requestStartTime
            return@fromCallable ChunkDownloadEvent.Success(output, requestTime)
        }
    }

    private fun markFileAsDownloaded(url: HttpUrl) {
        val request = checkNotNull(activeDownloads.get(url)) {
            "Active downloads does not have url: $url even though it was just downloaded"
        }

        if (!cacheHandler.markFileDownloaded(request.output)) {
            throw FileCacheException.CouldNotMarkFileAsDownloaded(request.output)
        }
    }
}