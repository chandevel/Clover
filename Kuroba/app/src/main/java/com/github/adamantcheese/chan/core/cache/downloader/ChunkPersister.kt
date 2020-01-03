package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import io.reactivex.Flowable

internal class ChunkPersister(
        private val fileManager: FileManager,
        private val cacheHandler: CacheHandler,
        private val activeDownloads: ActiveDownloads,
        private val verboseLogs: Boolean
) {

    fun writeChunksToCacheFile(
            url: String,
            chunkSuccessEvents: List<ChunkDownloadEvent.ChunkSuccess>,
            output: AbstractFile,
            requestStartTime: Long
    ): Flowable<ChunkDownloadEvent> {
        return Flowable.fromCallable {
            if (verboseLogs) {
                log(TAG, "writeChunksToCacheFile called ($url), chunks count = ${chunkSuccessEvents.size}")
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
                        logError(TAG, "Couldn't delete chunk file: ${event.chunkCacheFile.getFullPath()}")
                    }
                }
            }

            // Mark file as downloaded
            markFileAsDownloaded(url)

            val requestTime = System.currentTimeMillis() - requestStartTime
            return@fromCallable ChunkDownloadEvent.Success(output, requestTime)
        }
    }

    private fun markFileAsDownloaded(url: String) {
        val request = checkNotNull(activeDownloads.get(url)) {
            "Active downloads does not have url: ${url} even though " +
                    "it was just downloaded"
        }

        if (!cacheHandler.markFileDownloaded(request.output)) {
            throw FileCacheException.CouldNotMarkFileAsDownloaded(request.output)
        }
    }

    companion object {
        private const val TAG = "ChunkPersister"
    }
}