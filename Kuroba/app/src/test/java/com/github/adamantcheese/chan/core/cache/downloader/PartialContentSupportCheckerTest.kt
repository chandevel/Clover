package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.core.cache.createFileDownloadRequest
import com.github.adamantcheese.chan.core.cache.withServer
import com.github.adamantcheese.chan.utils.AndroidUtils
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
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
class PartialContentSupportCheckerTest {
    private val testModule = TestModule()

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var activeDownloads: ActiveDownloads
    private lateinit var partialContentSupportChecker: PartialContentSupportChecker

    @Before
    fun setUp() {
        AndroidUtils.init(testModule.provideApplication())

        okHttpClient = testModule.provideOkHttpClient()
        activeDownloads = testModule.provideActiveDownloads()
        partialContentSupportChecker = testModule.providePartialContentSupportChecker()
    }

    @After
    fun tearDown() {
        okHttpClient.dispatcher.cancelAll()
        activeDownloads.clear()
        partialContentSupportChecker.clear()
    }

    @Test
    fun `test check for batch request should return supportsPartialContentDownload == false`() {
        val url = "http://4chan.org/image1.jpg"
        val request = createFileDownloadRequest(url, isBatchDownload = true)
        activeDownloads.put(url, request)

        partialContentSupportChecker.check(url)
                .test()
                .assertValue { value ->
                    assertFalse(value.supportsPartialContentDownload)
                    true
                }
                .assertComplete()
                .assertNoErrors()
                .assertNoTimeout()
    }

    @Test
    fun `test small body size should return supportsPartialContentDownload == false with actual body size`() {
        withServer { server ->
            server.enqueue(
                    MockResponse()
                            .setResponseCode(200)
                            .addHeader("Accept-Ranges", "bytes")
                            .addHeader("Content-Length", "1024")
                            .addHeader("CF-Cache-Status", "HIT")
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)

            partialContentSupportChecker.check(url)
                    .test()
                    .awaitCount(1)
                    .assertValue { value ->
                        assertFalse(value.supportsPartialContentDownload)
                        assertEquals(1024L, value.length)
                        true
                    }
                    .assertComplete()
                    .assertNoErrors()
                    .assertNoTimeout()
                    .await()
        }
    }

    // FIXME: this test takes at least 300ms because I can't, for some reason, use TestScheduler
    //  with advanceTimeBy() operator because it just fails every time. Gotta figure out how to
    //  properly use TestScheduler to shift the time for the timeout() operator so it can be
    //  triggered without having to wait 300+ms.
    @Test
    fun `test HEAD request timeout should return supportsPartialContentDownload == false`() {
        withServer { server ->
            server.enqueue(
                    MockResponse()
                            .setResponseCode(200)
                            .addHeader("Accept-Ranges", "bytes")
                            .addHeader("Content-Length", "9999")
                            .addHeader("CF-Cache-Status", "HIT")
                            .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)


            partialContentSupportChecker.check(url)
                    .test()
                    .awaitCount(1)
                    .assertValue { value ->
                        assertFalse(value.supportsPartialContentDownload)
                        true
                    }
                    .assertComplete()
                    .assertNoErrors()
                    .assertNoTimeout()
                    .await()
        }
    }

    // FIXME: the same problem as with the test above ^^^
    @Test
    fun `test request cancellation`() {
        withServer { server ->
            server.enqueue(
                    MockResponse()
                            .setResponseCode(200)
                            .addHeader("Accept-Ranges", "bytes")
                            .addHeader("Content-Length", "9999")
                            .addHeader("CF-Cache-Status", "HIT")
                            .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)

            val testObserver = partialContentSupportChecker.check(url)
                    .test()

            Thread.sleep(150)
            request.cancelableDownload.cancel()

            testObserver
                    .awaitCount(1)
                    .assertValue { value ->
                        assertFalse(value.supportsPartialContentDownload)
                        true
                    }
                    .assertComplete()
                    .assertNoErrors()
                    .assertNoTimeout()

            val state = activeDownloads.get(url)!!.cancelableDownload.getState()
            assertTrue(state is DownloadState.Canceled)
        }
    }

    // FIXME: the same problem as with the test above ^^^
    @Test
    fun `test request stop`() {
        withServer { server ->
            server.enqueue(
                    MockResponse()
                            .setResponseCode(200)
                            .addHeader("Accept-Ranges", "bytes")
                            .addHeader("Content-Length", "9999")
                            .addHeader("CF-Cache-Status", "HIT")
                            .setHeadersDelay(300L, TimeUnit.MILLISECONDS)
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)

            val testObserver = partialContentSupportChecker.check(url)
                    .test()

            Thread.sleep(150)
            request.cancelableDownload.stop()

            testObserver
                    .awaitCount(1)
                    .assertValue { value ->
                        assertFalse(value.supportsPartialContentDownload)
                        true
                    }
                    .assertComplete()
                    .assertNoErrors()
                    .assertNoTimeout()

            val state = activeDownloads.get(url)!!.cancelableDownload.getState()
            assertTrue(state is DownloadState.Stopped)
        }
    }

    @Test
    fun `test when server returns 404`() {
        withServer { server ->
            server.enqueue(
                    MockResponse().setResponseCode(404)
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)

            partialContentSupportChecker.check(url)
                    .test()
                    .awaitCount(1)
                    .assertError { error ->
                        assertTrue(error is FileCacheException.FileNotFoundOnTheServerException)
                        true
                    }
                    .assertNotComplete()
                    .assertNoTimeout()
                    .assertNoValues()
                    .await()
        }
    }

    @Test
    fun `test everything ok`() {
        withServer { server ->
            server.enqueue(
                    MockResponse()
                            .setResponseCode(200)
                            .addHeader("Accept-Ranges", "bytes")
                            .addHeader("Content-Length", "9999")
                            .addHeader("CF-Cache-Status", "HIT")
            )

            server.start()

            val url = server.url("/image1.jpg").toString()
            val request = createFileDownloadRequest(url)
            activeDownloads.put(url, request)

            partialContentSupportChecker.check(url)
                    .test()
                    .awaitCount(1)
                    .assertValue { value ->
                        assertTrue(value.supportsPartialContentDownload)
                        assertFalse(value.notFoundOnServer)
                        assertEquals(9999L, value.length)
                        true
                    }
                    .assertComplete()
                    .assertNoErrors()
                    .assertNoTimeout()
                    .await()
        }
    }

}