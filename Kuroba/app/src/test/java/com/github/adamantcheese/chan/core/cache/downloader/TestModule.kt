package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.k1rakishou.fsaf.BadPathSymbolResolutionStrategy
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import com.github.k1rakishou.fsaf.manager.base_directory.DirectoryManager
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.concurrent.TimeUnit

class TestModule {
    private var okHttpClient: OkHttpClient? = null
    private var fileManager: FileManager? = null
    private var cacheHandler: CacheHandler? = null
    private var chunkDownloader: ChunkDownloader? = null
    private var activeDownloads: ActiveDownloads? = null
    private var concurrentChunkedFileDownloader: ConcurrentChunkedFileDownloader? = null
    private var partialContentSupportChecker: PartialContentSupportChecker? = null
    private var chunkPersister: ChunkPersister? = null
    private var chunkMerger: ChunkMerger? = null

    private var cacheDirFile: RawFile? = null
    private var chunksCacheDirFile: RawFile? = null

    fun provideApplication() = RuntimeEnvironment.application
    fun provideContext() = provideApplication().applicationContext

    internal fun provideChunkReader(): ChunkPersister {
        if (chunkPersister == null) {
            chunkPersister = ChunkPersister(
                    provideFileManager(),
                    provideCacheHandler(),
                    provideActiveDownloads(),
                    false
            )
        }

        return chunkPersister!!
    }

    internal fun provideChunkPersister(): ChunkMerger {
        if (chunkMerger == null) {
            chunkMerger = ChunkMerger(
                    provideFileManager(),
                    provideCacheHandler(),
                    provideActiveDownloads(),
                    false
            )
        }

        return chunkMerger!!
    }

    internal fun providePartialContentSupportChecker(): PartialContentSupportChecker {
        if (partialContentSupportChecker == null) {
            partialContentSupportChecker = PartialContentSupportChecker(
                    provideOkHttpClient(),
                    provideActiveDownloads(),
                    250L
            )
        }

        return partialContentSupportChecker!!
    }

    internal fun provideConcurrentChunkDownloader(): ConcurrentChunkedFileDownloader {
        if (concurrentChunkedFileDownloader == null) {
            concurrentChunkedFileDownloader = ConcurrentChunkedFileDownloader(
                    provideFileManager(),
                    provideChunkDownloader(),
                    provideChunkReader(),
                    provideChunkPersister(),
                    Schedulers.single(),
                    false,
                    provideActiveDownloads(),
                    provideCacheHandler()
            )
        }

        return concurrentChunkedFileDownloader!!
    }

    internal fun provideActiveDownloads(): ActiveDownloads {
        if (activeDownloads == null) {
            activeDownloads = ActiveDownloads()
        }

        return activeDownloads!!
    }

    internal fun provideChunkDownloader(): ChunkDownloader {
        if (chunkDownloader == null) {
            chunkDownloader = ChunkDownloader(
                    provideOkHttpClient(),
                    provideActiveDownloads(),
                    false
            )
        }

        return chunkDownloader!!
    }

    fun provideFileManager(): FileManager {
        if (fileManager == null) {
            fileManager = FileManager(
                    provideContext(),
                    BadPathSymbolResolutionStrategy.ThrowAnException,
                    DirectoryManager()
            )
        }

        return fileManager!!
    }

    fun provideCacheHandler(): CacheHandler {
        if (cacheHandler == null) {
            cacheHandler = CacheHandler(
                    provideFileManager(),
                    provideCacheDirFile(),
                    provideChunksCacheDirFile(),
                    false
            )
        }

        return cacheHandler!!
    }

    fun provideCacheDirFile(): RawFile {
        if (cacheDirFile == null) {
            val fileMan = provideFileManager()

            cacheDirFile = fileMan.fromRawFile(File(provideContext().cacheDir, "cache_dir"))
            assertNotNull(fileMan.create(cacheDirFile!!))
            assertTrue(fileMan.deleteContent(cacheDirFile!!))
        }

        return cacheDirFile!!
    }

    fun provideChunksCacheDirFile(): RawFile {
        if (chunksCacheDirFile == null) {
            val fileMan = provideFileManager()

            chunksCacheDirFile = fileMan.fromRawFile(File(provideContext().cacheDir, "chunks_cache_dir"))
            assertNotNull(fileMan.create(chunksCacheDirFile!!))
            assertTrue(fileMan.deleteContent(chunksCacheDirFile!!))
        }

        return chunksCacheDirFile!!
    }

    fun provideOkHttpClient(): OkHttpClient {
        if (okHttpClient == null) {
            okHttpClient = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(5, TimeUnit.SECONDS)
                    .build()
        }

        return okHttpClient!!
    }
}