package com.github.adamantcheese.chan.core.cache.downloader

import com.github.adamantcheese.chan.utils.Logger

internal fun log(tag: String, message: String) {
    Logger.d(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
}

internal fun logError(tag: String, message: String, error: Throwable? = null) {
    if (error == null) {
        Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message))
    } else {
        Logger.e(tag, String.format("[%s]: %s", Thread.currentThread().name, message), error)
    }
}