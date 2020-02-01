package com.github.adamantcheese.chan.core.cache.downloader

internal data class PartialContentCheckResult(
        val supportsPartialContentDownload: Boolean,
        val notFoundOnServer: Boolean = false,
        val length: Long = -1L
) {

    fun couldDetermineFileSize(): Boolean = length >= 0

}