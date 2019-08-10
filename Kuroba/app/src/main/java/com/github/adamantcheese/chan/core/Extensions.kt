package com.github.adamantcheese.chan.core

import android.net.Uri


fun String.extension(): String? {
    val index = this.indexOfLast { ch -> ch == '.' }
    if (index == -1) {
        return null
    }

    return this.substring(index + 1)
}

fun Uri.Builder.appendManyEncoded(segments: List<String>): Uri.Builder {
    for (segment in segments) {
        this.appendEncodedPath(segment)
    }

    return this
}