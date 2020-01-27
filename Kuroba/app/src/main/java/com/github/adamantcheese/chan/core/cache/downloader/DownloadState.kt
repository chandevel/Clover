package com.github.adamantcheese.chan.core.cache.downloader

sealed class DownloadState {
    object Running : DownloadState()
    /**
     * Stopped is kinda the same as Canceled, the only difference is that we don't remove the cache
     * file right away because we use that cache file ti fill up the WebmStreamingDataSource
     * */
    object Stopped : DownloadState()

    /**
     * Cancels the download and deletes the file
     * */
    object Canceled : DownloadState()
}