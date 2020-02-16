package com.github.adamantcheese.chan.core.cache

import com.github.adamantcheese.chan.core.cache.downloader.ActiveDownloads
import com.github.adamantcheese.chan.core.cache.downloader.FileCacheException
import com.github.adamantcheese.chan.core.cache.downloader.FileDownloadResult
import junit.framework.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {

    @Test
    fun `FileNotFoundOnTheServerException must be mapped into FileDownloadResult KnownException`() {
        val url = "test.com"
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