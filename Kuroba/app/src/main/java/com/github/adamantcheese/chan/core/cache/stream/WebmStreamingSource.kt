package com.github.adamantcheese.chan.core.cache.stream

import android.net.Uri
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheListener
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.MediaSourceCallback
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.RawFile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.FileDataSource
import java.io.IOException

class WebmStreamingSource(
        private val fileManager: FileManager,
        private val fileCacheV2: FileCacheV2,
        private val cacheHandler: CacheHandler
) {

    fun createMediaSource(postImage: PostImage, callback: MediaSourceCallback) {
        val uri = Uri.parse(postImage.imageUrl.toString())
        val alreadyExists = cacheHandler.exists(postImage.imageUrl)
        val rawFile = cacheHandler.getOrCreateCacheFile(postImage.imageUrl)
        val fileCacheSource = WebmStreamingDataSource(uri, rawFile, fileManager)

        fileCacheSource.addListener { file ->
            BackgroundUtils.ensureMainThread()
            cacheHandler.fileWasAdded(file.length())
        }

        if (alreadyExists && rawFile != null && cacheHandler.isAlreadyDownloaded(rawFile)) {
            Logger.d(TAG, "Loaded from file cache")

            loadFromCacheFile(rawFile, callback)
            return
        }

        val cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
                postImage.imageUrl,
                object : FileCacheListener() {
                    override fun onSuccess(file: RawFile?, immediate: Boolean) {
                        Logger.d(TAG, "createMediaSource() Loading just downloaded file after stop()")
                        BackgroundUtils.ensureMainThread()

                        // The webm file is already completely downloaded, just use it from the disk
                        callback.onMediaSourceReady(
                                ProgressiveMediaSource.Factory { fileCacheSource }
                                        .createMediaSource(MediaItem.fromUri(uri))
                        )
                    }

                    override fun onStop(file: AbstractFile) {
                        BackgroundUtils.ensureMainThread()

                        startLoadingFromNetwork(file, fileCacheSource, callback, uri)
                    }

                    override fun onNotFound() {
                        BackgroundUtils.ensureMainThread()
                        callback.onError(IOException("Not found"))
                    }

                    override fun onFail(exception: Exception) {
                        Logger.d(TAG, "createMediaSource() onFail $exception")

                        BackgroundUtils.ensureMainThread()
                        callback.onError(exception)
                    }
                })

        if (cancelableDownload == null) {
            Logger.d(TAG, "createMediaSource() cancelableDownload == null")
            return
        }

        // Trigger the onStop() callback so that we can load everything we have managed to download
        // via FileCache into the WebmStreamingDataSource
        cancelableDownload.stop()
    }

    private fun startLoadingFromNetwork(
            file: AbstractFile,
            fileCacheSource: WebmStreamingDataSource,
            callback: MediaSourceCallback,
            uri: Uri
    ) {
        // The webm file is either partially downloaded or is not downloaded at all.
        // We take whatever there is and load it into the WebmStreamingDataSource so
        // we don't need to redownload the bytes that have already been downloaded
        val exists = fileManager.exists(file)
        val fileLength = fileManager.getLength(file)

        Logger.d(TAG,
                "createMediaSource() Loading partially downloaded file after stop(), " +
                        "fileLength = $fileLength")

        if (exists && fileLength > 0L) {
            try {
                fileManager.getInputStream(file)?.use { inputStream ->
                    fileCacheSource.fillCache(fileLength, inputStream)
                } ?: throw IOException("Couldn't get input stream for file " +
                        "(${file.getFullPath()})")
            } catch (error: IOException) {
                Logger.e(TAG, "createMediaSource() Failed to fill cache!", error)
            }
        }

        callback.onMediaSourceReady(
                ProgressiveMediaSource.Factory { fileCacheSource }
                        .createMediaSource(MediaItem.fromUri(uri))
        )
    }

    private fun loadFromCacheFile(rawFile: RawFile, callback: MediaSourceCallback) {
        Logger.d(TAG, "createMediaSource() Loading already downloaded file from the disk")
        val fileUri = Uri.parse(rawFile.getFullPath())

        callback.onMediaSourceReady(
                ProgressiveMediaSource.Factory { FileDataSource() }
                        .createMediaSource(MediaItem.fromUri(fileUri))
        )
    }

    companion object {
        private const val TAG = "WebmStreamingSource"
    }
}