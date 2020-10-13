package com.github.adamantcheese.chan.core.cache

import com.github.adamantcheese.chan.core.cache.downloader.ActiveDownloads
import com.github.adamantcheese.chan.core.cache.downloader.FileCacheException
import com.github.adamantcheese.chan.core.cache.downloader.FileDownloadResult
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun `FileNotFoundOnTheServerException must be mapped into FileDownloadResult KnownException`() {
        val url = "https://www.test.com/test.jpg".toHttpUrl()
        val activeDownloads = ActiveDownloads()

        val result = ErrorMapper.mapError(
            url,
            FileCacheException.FileNotFoundOnTheServerException(),
            activeDownloads
        )

        assertTrue(result is FileDownloadResult.KnownException)
        result as FileDownloadResult.KnownException

        assertTrue(result.fileCacheException is FileCacheException.FileNotFoundOnTheServerException)
    }

}