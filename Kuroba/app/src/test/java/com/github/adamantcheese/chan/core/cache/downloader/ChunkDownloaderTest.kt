package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.PartialContentOkHttpDispatcher
import com.github.adamantcheese.chan.core.cache.createFileDownloadRequest
import com.github.adamantcheese.chan.core.cache.withServer
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ChunkDownloaderTest {
    private val testModule = TestModule()

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var chunkDownloader: ChunkDownloader
    private lateinit var activeDownloads: ActiveDownloads
    private lateinit var cacheHandler: CacheHandler
    private lateinit var fileManager: FileManager
    private lateinit var chunksCacheDirFile: RawFile

    @Before
    fun init() {
        AndroidUtils.init(testModule.provideApplication())

        okHttpClient = testModule.provideOkHttpClient()
        activeDownloads = testModule.provideActiveDownloads()
        chunkDownloader = testModule.provideChunkDownloader()
        cacheHandler = testModule.provideCacheHandler()
        fileManager = testModule.provideFileManager()
        chunksCacheDirFile = testModule.provideChunksCacheDirFile()
    }

    @After
    fun tearDown() {
        okHttpClient.dispatcher.cancelAll()
        activeDownloads.clear()
    }

    @Test
    fun `test get couple of chunks from server and compare to original image byte by byte`() {
        withServer { server ->
            val imageName = "test_img1.jpg"
            val chunks = listOf(
                    Chunk(0, 100),
                    Chunk(1024, 1555),
                    Chunk(32766, 33000)
            )

            server.dispatcher = PartialContentOkHttpDispatcher()
            server.start()

            val url = server.url("/${imageName}").toString()
            val request = createFileDownloadRequest(url, chunks.size)
            activeDownloads.put(url, request)

            val wholeFile = javaClass.classLoader.getResourceAsStream(imageName)
                    .use { it.readBytes() }

            chunks.forEach { chunk ->
                val testObserver = chunkDownloader.downloadChunk(url, chunk, chunks.size)
                        .subscribeOn(Schedulers.single())
                        .test()

                val (events, errors, completes) = testObserver
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                assertTrue(errors.isEmpty())
                assertEquals(1, completes.size)
                assertEquals(1, events.size)

                val response = events.first() as Response
                assertEquals(206, response.code)

                val body = checkNotNull(response.body)
                assertEquals(chunk.chunkSize(), body.contentLength())
                val bytesFromServer = body.bytes()

                val start = chunk.start.toInt()
                val end = chunk.realEnd.toInt()
                val bytesFromFile = wholeFile.slice(start until end).toByteArray()

                assertArrayEquals(bytesFromFile, bytesFromServer)
            }

            assertEquals(chunks.size, server.requestCount)
        }
    }

    @Test
    fun `test should not download anything when request is canceled`() {
        withServer { server ->
            val imageName = "test_img1.jpg"
            val url = server.url("/${imageName}").toString()
            val chunk = Chunk(999, 9999)
            val chunksCount = 4

            val output = cacheHandler.getOrCreateCacheFile(url) as RawFile
            val request = createFileDownloadRequest(url, chunksCount, file = output)
            activeDownloads.put(url, request)
            request.cancelableDownload.cancel()

            val testObserver = chunkDownloader.downloadChunk(url, chunk, chunksCount)
                    .subscribeOn(Schedulers.single())
                    .test()

            val (events, errors, completes) = testObserver
                    .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                    .events

            assertTrue(events.isEmpty())
            assertTrue(completes.isEmpty())
            assertEquals(1, errors.size)
            assertTrue(errors.first() is FileCacheException.CancellationException)
            assertFalse(cacheHandler.isAlreadyDownloaded(output))
            assertTrue(fileManager.listFiles(chunksCacheDirFile).isEmpty())

            assertEquals(0, server.requestCount)
        }
    }

    companion object {
        private const val MAX_AWAIT_TIME_SECONDS = 5L
    }
}