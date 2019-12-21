package com.github.adamantcheese.chan.core.cache

import android.net.Uri
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import java.io.IOException

class WebmStreamingSource(
        private val fileManager: FileManager,
        private val fileCacheV2: FileCacheV2,
        private val cacheHandler: CacheHandler
) {
    fun createMediaSource(videoUrl: String, callback: MediaSourceCallback) {
        val uri = Uri.parse(videoUrl)
        val alreadyExists = cacheHandler.exists(videoUrl)
        val rawFile = cacheHandler.getOrCreateCacheFile(videoUrl)
        val fileCacheSource = FileCacheDataSource(uri, rawFile, fileManager)

        fileCacheSource.addListener { file ->
            cacheHandler.fileWasAdded(file.length())
        }

        if (alreadyExists
                && rawFile != null
                && cacheHandler.isAlreadyDownloaded(rawFile)) {
            val fileUri = Uri.parse(rawFile.getFullPath())

            callback.onMediaSourceReady(
                    ProgressiveMediaSource.Factory(DataSource.Factory { FileDataSource() })
                            .createMediaSource(fileUri)
            )
            return
        }

        val cancelableDownload = fileCacheV2.enqueueDownloadFileRequest(
                videoUrl,
                object : FileCacheListener() {
                    override fun onSuccess(file: RawFile?) {
                        callback.onMediaSourceReady(
                                ProgressiveMediaSource.Factory(DataSource.Factory { fileCacheSource })
                                        .createMediaSource(uri)
                        )
                    }

                    override fun onStop(file: RawFile) {
                        val exists = fileManager.exists(file)
                        val fileLength = fileManager.getLength(file)

                        if (exists && fileLength > 0L) {
                            try {
                                fileCacheSource.fillCache(file)
                            } catch (error: IOException) {
                                Logger.e(TAG, "Failed to fill cache!", error)
                            }
                        }

                        callback.onMediaSourceReady(
                                ProgressiveMediaSource.Factory(DataSource.Factory { fileCacheSource })
                                        .createMediaSource(uri)
                        )
                    }

                    override fun onFail(exception: Exception) {
                        callback.onError(exception)
                    }
                })

        if (cancelableDownload == null) {
            return
        }

        cancelableDownload.stop()
    }

    companion object {
        private const val TAG = "WebmStreamingSource"
    }
}