package com.github.adamantcheese.chan.core.cache.downloader

internal data class PartialContentCheckResult(
        val supportsPartialContentDownload: Boolean,
        val length: Long = -1L
)