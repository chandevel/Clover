package com.github.adamantcheese.chan.core.cache.downloader

sealed class DownloadState {
    object Running : DownloadState()
    object Stopped : DownloadState()
    object Canceled : DownloadState()
}