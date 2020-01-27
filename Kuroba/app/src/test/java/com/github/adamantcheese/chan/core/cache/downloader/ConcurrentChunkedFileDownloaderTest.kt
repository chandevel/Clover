package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.CacheHandler
import com.github.adamantcheese.chan.core.cache.PartialContentOkHttpDispatcher
import com.github.adamantcheese.chan.core.cache.createFileDownloadRequest
import com.github.adamantcheese.chan.core.cache.withServer
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.RawFile
import io.reactivex.subscribers.TestSubscriber
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit


@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ConcurrentChunkedFileDownloaderTest {
    private val testModule = TestModule()

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var activeDownloads: ActiveDownloads
    private lateinit var fileManager: FileManager
    private lateinit var cacheHandler: CacheHandler
    private lateinit var concurrentChunkedFileDownloader: ConcurrentChunkedFileDownloader
    private lateinit var cacheDirFile: RawFile
    private lateinit var chunksCacheDirFile: RawFile

    @Before
    fun setUp() {
        AndroidUtils.init(testModule.provideApplication())

        activeDownloads = testModule.provideActiveDownloads()
        fileManager = testModule.provideFileManager()
        okHttpClient = testModule.provideOkHttpClient()
        cacheHandler = testModule.provideCacheHandler()
        cacheDirFile = testModule.provideCacheDirFile()
        chunksCacheDirFile = testModule.provideChunksCacheDirFile()
        concurrentChunkedFileDownloader = testModule.provideConcurrentChunkDownloader()
    }

    @After
    fun tearDown() {
        okHttpClient.dispatcher.cancelAll()
        activeDownloads.clear()
        cacheHandler.clearCache()
    }

    @Test
    fun `test cancel one chunk download request`() {
        withServer { server ->
            val buffer = Buffer()
                    .readFrom(javaClass.classLoader.getResourceAsStream("test_img1.jpg"))

            val response = MockResponse()
                    .setResponseCode(200)
                    .setBody(buffer)

            singleChunkTestProlog(server, response) { url, output, request, testObserver ->
                request.cancelableDownload.cancel()

                val (events, errors, completes) = testObserver
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                assertEquals(1, events.size)
                assertEquals(1, errors.size)
                assertTrue(completes.isEmpty())

                assertTrue(events.first() is FileDownloadResult.Start)
                assertTrue(errors.first() is FileCacheException.CancellationException)

                val cacheFiles = fileManager.listFiles(cacheDirFile)
                assertTrue(fileManager.getName(cacheFiles[0]).endsWith(CacheHandler.CACHE_EXTENSION))
                assertTrue(fileManager.getName(cacheFiles[1]).endsWith(CacheHandler.CACHE_META_EXTENSION))
                assertFalse(cacheHandler.isAlreadyDownloaded(output))

                assertEquals(2, cacheFiles.size)
                assertTrue(fileManager.listFiles(chunksCacheDirFile).isEmpty())
            }
        }
    }

    @Test
    fun `test bad status code in server response should cancel the request`() {
        withServer { server ->
            val response = MockResponse().setResponseCode(404)

            singleChunkTestProlog(server, response) { url, output, request, testObserver ->
                val (events, errors, completes) = testObserver
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                assertEquals(1, events.size)
                assertEquals(1, errors.size)
                assertTrue(completes.isEmpty())

                assertTrue(events.first() is FileDownloadResult.Start)
                assertTrue(errors.first() is FileCacheException.FileNotFoundOnTheServerException)

                val cacheFiles = fileManager.listFiles(cacheDirFile)
                assertTrue(fileManager.getName(cacheFiles[0]).endsWith(CacheHandler.CACHE_EXTENSION))
                assertTrue(fileManager.getName(cacheFiles[1]).endsWith(CacheHandler.CACHE_META_EXTENSION))
                assertFalse(cacheHandler.isAlreadyDownloaded(output))

                assertEquals(2, cacheFiles.size)
                assertTrue(fileManager.listFiles(chunksCacheDirFile).isEmpty())
            }
        }
    }

    @Test
    fun `test enqueue four multi chunk requests and cancel two of the should only download the two not canceled images`() {
        data class ExtraInfo(val imageName: String, val url: String, val output: RawFile)
        data class ResultData(
                val events: List<Any>,
                val errors: List<Any>,
                val completes: List<Any>,
                val extraInfo: ExtraInfo
        )

        withServer { server ->
            server.dispatcher = PartialContentOkHttpDispatcher()
            server.start()
            val chunksCount = 2

            val imageNameList = listOf(
                    "test_img1.jpg",
                    "test_img2.jpg",
                    "test_img3.jpg",
                    "test_img4.jpg"
            )

            val imageSizeList = imageNameList.map { imageName ->
                return@map javaClass.classLoader.getResourceAsStream(imageName).use { stream ->
                    stream.available()
                }
            }

            val sizeMap = imageNameList.zip(imageSizeList).toMap()

            // Start 4 requests
            val testObservers = imageNameList.map { imageName ->
                val url = server.url("/${imageName}").toString()
                val output = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
                val request = createFileDownloadRequest(url, chunksCount = chunksCount, file = output)
                activeDownloads.put(url, request)

                return@map concurrentChunkedFileDownloader.download(
                        PartialContentCheckResult(
                                supportsPartialContentDownload = true,
                                notFoundOnServer = false,
                                length = sizeMap[imageName]!!.toLong()
                        ),
                        url,
                        true
                ) to ExtraInfo(imageName, url, output)
            }.map { (flowable, extraInfo) ->
                flowable.test() to extraInfo
            }

            // Cancel first two of them
            val toCancelObservers = testObservers.take(2)
            toCancelObservers.forEach { (_, extraInfo) ->
                activeDownloads.get(extraInfo.url)!!.cancelableDownload.cancel()
            }

            // Wait for all the events
            val resultDataList = testObservers.map { (observer, extraInfo) ->
                val (events, errors, completes) = observer
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                return@map ResultData(
                        events = events,
                        errors = errors,
                        completes = completes,
                        extraInfo = extraInfo
                )
            }

            val allRequests = activeDownloads.getAll()

            resultDataList.forEach { (events, errors, completes, extraInfo) ->
                val canceled = toCancelObservers.any { (_, info) ->
                    info.url == extraInfo.url
                }

                if (canceled) {
                    // Check that canceled requests are actually canceled
                    assertEquals(1, events.size)
                    assertEquals(1, errors.size)
                    assertTrue(completes.isEmpty())

                    assertTrue(events.first() is FileDownloadResult.Start)
                    assertTrue(errors.first() is FileCacheException.CancellationException)
                } else {
                    // Check that not canceled requests have actually succeeded
                    assertTrue(errors.isEmpty())
                    assertEquals(1, completes.size)

                    val progressEventsCount = events.count { event ->
                        event is FileDownloadResult.Progress
                    }

                    // Check that the amount of progress events is as expected
                    assertEquals(progressEventsCount, events.size - 2)

                    // Check that first event is Start event, last event is Success and only the
                    // Progress events are between the two
                    assertTrue(events.first() is FileDownloadResult.Start)
                    assertTrue(events.last() is FileDownloadResult.Success)
                    assertTrue(events.drop(1).dropLast(1).all { event ->
                        event is FileDownloadResult.Progress
                    })

                    val startEvents = events.filterIsInstance<FileDownloadResult.Start>()
                    val progressEvents = events.filterIsInstance<FileDownloadResult.Progress>()
                    val successEvents = events.filterIsInstance<FileDownloadResult.Success>()
                    assertEquals(
                            events.size,
                            startEvents.size + progressEvents.size + successEvents.size
                    )
                }

                // Find only file related to the current request since there will be 8 of them instead
                // of two
                val cacheFiles = fileManager.listFiles(cacheDirFile).filter { file ->
                    return@filter fileManager.getName(file).contains(cacheHandler.hashUrl(extraInfo.url))
                }

                // Check that they are find
                assertEquals(2, cacheFiles.size)
                assertTrue(fileManager.getName(cacheFiles[0]).endsWith(CacheHandler.CACHE_EXTENSION))
                assertTrue(fileManager.getName(cacheFiles[1]).endsWith(CacheHandler.CACHE_META_EXTENSION))
                assertTrue(fileManager.listFiles(chunksCacheDirFile).isEmpty())


                // Check that canceled requests have not been downloaded and not canceled have
                // been downloaded
                if (canceled) {
                    assertFalse(cacheHandler.isAlreadyDownloaded(extraInfo.output))
                } else {
                    assertTrue(cacheHandler.isAlreadyDownloaded(extraInfo.output))
                }

                // Check that we still have requests in the map
                assertNotNull(activeDownloads.get(extraInfo.url))
            }

            // Check that the request info is actual and correct - two requests are canceled and
            // two are still running
            assertEquals(2, allRequests.count { request ->
                request.cancelableDownload.getState() == DownloadState.Canceled
            })
            assertEquals(2, allRequests.count { request ->
                request.cancelableDownload.getState() == DownloadState.Running
            })
        }
    }

    @Test
    fun `test download the whole image in one chunk when everything is ok`() {
        withServer { server ->
            val buffer = Buffer()
                    .readFrom(javaClass.classLoader.getResourceAsStream("test_img1.jpg"))

            val response = MockResponse()
                    .setResponseCode(200)
                    .setBody(buffer)

            singleChunkTestProlog(server, response) { url, output, request, testObserver ->
                val (events, errors, completes) = testObserver
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                assertTrue(errors.isEmpty())
                assertEquals(1, completes.size)
                assertEquals(11, events.size)

                assertTrue(events.first() is FileDownloadResult.Start)
                assertTrue(events.last() is FileDownloadResult.Success)
                assertTrue(events.drop(1).dropLast(1).all { event -> event is FileDownloadResult.Progress })

                val startEvents = events.filterIsInstance<FileDownloadResult.Start>()
                val progressEvents = events.filterIsInstance<FileDownloadResult.Progress>()
                val successEvents = events.filterIsInstance<FileDownloadResult.Success>()
                assertEquals(events.size, startEvents.size + progressEvents.size + successEvents.size)

                checkCacheFilesAreOk(output, url)
            }
        }
    }

    @Test
    fun `test download the whole image in multiple chunks when everything is ok`() {
        withServer { server ->
            val imageName = "test_img1.jpg"

            val fileSize = javaClass.classLoader.getResourceAsStream(imageName).use { stream ->
                stream.available()
            }

            multiChunkTestPrologue(server, fileSize.toLong(), 4, imageName) { url, output, request, testObserver ->
                val (events, errors, completes) = testObserver
                        .awaitDone(MAX_AWAIT_TIME_SECONDS, TimeUnit.SECONDS)
                        .events

                assertTrue(errors.isEmpty())
                assertEquals(1, completes.size)
                assertEquals(30, events.size)

                assertTrue(events.first() is FileDownloadResult.Start)
                assertTrue(events.last() is FileDownloadResult.Success)
                assertTrue(events.drop(1).dropLast(1).all { event -> event is FileDownloadResult.Progress })

                val startEvents = events.filterIsInstance<FileDownloadResult.Start>()
                val progressEvents = events.filterIsInstance<FileDownloadResult.Progress>()
                val successEvents = events.filterIsInstance<FileDownloadResult.Success>()
                assertEquals(events.size, startEvents.size + progressEvents.size + successEvents.size)
                assertEquals(4, progressEvents.distinctBy { event -> event.chunkIndex }.size)

                checkCacheFilesAreOk(output, url)
            }
        }
    }

    private fun checkCacheFilesAreOk(output: RawFile, url: String) {
        val cacheFiles = fileManager.listFiles(cacheDirFile)
        assertEquals(2, cacheFiles.size)
        assertTrue(fileManager.getName(cacheFiles[0]).endsWith(CacheHandler.CACHE_EXTENSION))
        assertTrue(fileManager.getName(cacheFiles[1]).endsWith(CacheHandler.CACHE_META_EXTENSION))
        assertTrue(fileManager.listFiles(chunksCacheDirFile).isEmpty())
        assertTrue(cacheHandler.isAlreadyDownloaded(output))

        cacheHandler.clearCache()
        assertTrue(fileManager.listFiles(cacheDirFile).isEmpty())

        assertNotNull(activeDownloads.get(url))
    }

    private fun multiChunkTestPrologue(
            server: MockWebServer,
            fileSize: Long,
            chunksCount: Int,
            imageName: String,
            func: (String, RawFile, FileDownloadRequest, TestSubscriber<FileDownloadResult>) -> Unit
    ) {
        server.dispatcher = PartialContentOkHttpDispatcher()
        server.start()

        val url = server.url("/${imageName}").toString()
        val output = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
        val request = createFileDownloadRequest(url, chunksCount = chunksCount, file = output)
        activeDownloads.put(url, request)

        val testObserver = concurrentChunkedFileDownloader.download(
                PartialContentCheckResult(
                        supportsPartialContentDownload = true,
                        notFoundOnServer = false,
                        length = fileSize
                ),
                url,
                true
        )
                .test()

        func(url, output, request, testObserver)
    }

    private fun singleChunkTestProlog(
            server: MockWebServer,
            response: MockResponse,
            func: (String, RawFile, FileDownloadRequest, TestSubscriber<FileDownloadResult>) -> Unit
    ) {
        server.enqueue(response)
        server.start()

        val url = server.url("/image1.jpg").toString()
        val output = checkNotNull(cacheHandler.getOrCreateCacheFile(url))
        val request = createFileDownloadRequest(url, chunksCount = 1, file = output)
        activeDownloads.put(url, request)

        val testObserver = concurrentChunkedFileDownloader.download(
                PartialContentCheckResult(
                        supportsPartialContentDownload = false,
                        notFoundOnServer = false,
                        length = -1
                ),
                url,
                true
        )
                .test()

        func(url, output, request, testObserver)
    }

    companion object {
        private const val MAX_AWAIT_TIME_SECONDS = 5L
    }
}