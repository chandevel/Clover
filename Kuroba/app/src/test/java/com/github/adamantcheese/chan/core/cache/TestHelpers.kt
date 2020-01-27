package com.github.adamantcheese.chan.core.cache

import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload
import com.github.adamantcheese.chan.core.cache.downloader.DownloadRequestExtraInfo
import com.github.adamantcheese.chan.core.cache.downloader.FileDownloadRequest
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal fun withServer(func: (MockWebServer) -> Unit) {
    val server = MockWebServer()

    try {
        func(server)
    } finally {
        server.shutdown()
    }
}

internal fun createFileDownloadRequest(
        url: String,
        chunksCount: Int = 1,
        isBatchDownload: Boolean = false,
        file: AbstractFile = mock()
): FileDownloadRequest {
    val executor = mock<ExecutorService>()

    val cancelableDownload = CancelableDownload(
            url,
            executor,
            AtomicBoolean(isBatchDownload)
    )

    return spy(
            FileDownloadRequest(
                    url,
                    file,
                    AtomicInteger(chunksCount),
                    AtomicLong(0),
                    AtomicLong(0),
                    cancelableDownload,
                    DownloadRequestExtraInfo()
            )
    )
}