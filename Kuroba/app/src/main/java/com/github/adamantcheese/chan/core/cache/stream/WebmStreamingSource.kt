package com.github.adamantcheese.chan.core.cache.stream

import android.net.Uri
import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.FileCacheListener
import com.github.adamantcheese.chan.core.cache.FileCacheV2
import com.github.adamantcheese.chan.core.cache.MediaSourceCallback
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.model.PostImage
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.file.Segment
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import io.reactivex.Completable
import java.io.IOException

class WebmStreamingSource(
        private val fileManager: FileManager,
        private val fileCacheV2: FileCacheV2,
        private val cacheHandler: CacheHandler
) {
    fun createMediaSource(loadable: Loadable, postImage: PostImage, callback: MediaSourceCallback) {
        val videoUrl = postImage.imageUrl.toString()
        val uri = Uri.parse(videoUrl)
        val alreadyExists = cacheHandler.exists(videoUrl)
        val rawFile = cacheHandler.getOrCreateCacheFile(videoUrl)
        val fileCacheSource = WebmStreamingDataSource(uri, rawFile, fileManager)

        fileCacheSource.addListener { file ->
            BackgroundUtils.ensureMainThread()
            cacheHandler.fileWasAdded(file.length())
        }

        if (alreadyExists && rawFile != null && cacheHandler.isAlreadyDownloaded(rawFile)) {
            loadFromCacheFile(rawFile, callback)
            return
        }

        if (loadable.isLocal || loadable.isDownloading) {
            Completable
                    .fromRunnable { loadFromLocalThread(postImage, loadable, callback) }
                    .subscribe()

            return
        }

        val cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(
                videoUrl,
                object : FileCacheListener() {
                    override fun onSuccess(file: RawFile?) {
                        Logger.d(TAG, "createMediaSource() Loading just downloaded file after stop()")
                        BackgroundUtils.ensureMainThread()

                        // The webm file is already completely downloaded, just use it from the disk
                        callback.onMediaSourceReady(
                                ProgressiveMediaSource.Factory(DataSource.Factory { fileCacheSource })
                                        .createMediaSource(uri)
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
                        Logger.d(TAG, "createMediaSource() onFail ${exception}")

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
                ProgressiveMediaSource.Factory(DataSource.Factory { fileCacheSource })
                        .createMediaSource(uri)
        )
    }

    private fun loadFromCacheFile(rawFile: RawFile, callback: MediaSourceCallback) {
        Logger.d(TAG, "createMediaSource() Loading already downloaded file from the disk")
        val fileUri = Uri.parse(rawFile.getFullPath())

        callback.onMediaSourceReady(
                ProgressiveMediaSource.Factory(DataSource.Factory { FileDataSource() })
                        .createMediaSource(fileUri)
        )
    }

    private fun loadFromLocalThread(
            postImage: PostImage,
            loadable: Loadable,
            callback: MediaSourceCallback
    ) {
        Logger.d(TAG, "createMediaSource() Loading local thread file")

        val filename = ThreadSaveManager.formatOriginalImageName(
                postImage.serverFilename,
                postImage.extension
        )

        val imagesSubDirSegments = ThreadSaveManager.getImagesSubDir(loadable)
        val segments: MutableList<Segment> = ArrayList(imagesSubDirSegments).apply {
            add(FileSegment(filename))
        }

        val baseDirFile = fileManager.newBaseDirectoryFile(
                LocalThreadsBaseDirectory::class.java
        )

        if (baseDirFile == null) {
            BackgroundUtils.runOnMainThread {
                callback.onError(
                        IOException("createMediaSource() Couldn't create a file " +
                                "inside local threads base directory")
                )
            }

            return
        }

        val localImgFile = baseDirFile.clone(segments)
        val exists = fileManager.exists(localImgFile)
        val isFile = fileManager.isFile(localImgFile)
        val canRead = fileManager.canRead(localImgFile)
        val isLocalFileOk = exists && isFile && canRead

        if (!isLocalFileOk) {
            BackgroundUtils.runOnMainThread {
                callback.onError(
                        IOException("createMediaSource() Couldn't open webm from a local thread " +
                                "(exists = $exists, isFile = $isFile, canRead = $canRead)")
                )
            }

            return
        }

        // FileDataSource uses RandomAccessFile internally, which, apparently cannot read files
        // stored via SAF, so we have to copy the file into the cache directory to use it
        val videoUrl = postImage.imageUrl.toString()
        val destination = createDestinationFile(videoUrl, localImgFile, callback)
        if (destination == null) {
            // No need to call callbacks it's already called in createDestinationFile
            return
        }

        val fileUri = Uri.parse(destination.getFullPath())

        BackgroundUtils.runOnMainThread {
            callback.onMediaSourceReady(
                    ProgressiveMediaSource.Factory(DataSource.Factory { FileDataSource() })
                            .createMediaSource(fileUri)
            )
        }
    }

    private fun createDestinationFile(
            videoUrl: String,
            localImgFile: AbstractFile,
            callback: MediaSourceCallback
    ): RawFile? {
        if (localImgFile is RawFile) {
            return localImgFile
        }

        val destination = cacheHandler.getOrCreateCacheFile(videoUrl)
        if (destination == null) {
            BackgroundUtils.runOnMainThread {
                callback.onError(
                        IOException("createMediaSource() Couldn't create destination file for webm")
                )
            }

            return null
        }

        val destinationPath = destination.getFullPath()

        if (!fileManager.copyFileContents(localImgFile, destination)) {
            BackgroundUtils.runOnMainThread {
                callback.onError(
                        IOException("createMediaSource() Couldn't copy webm into file " +
                                "cache directory ($destinationPath)")
                )
            }

            return null
        }

        if (!cacheHandler.markFileDownloaded(destination)) {
            BackgroundUtils.runOnMainThread {
                callback.onError(
                        IOException("createMediaSource() Couldn't mark destination file " +
                                "as downloaded ($destinationPath)")
                )
            }

            return null
        }

        return destination
    }

    companion object {
        private const val TAG = "WebmStreamingSource"
    }
}