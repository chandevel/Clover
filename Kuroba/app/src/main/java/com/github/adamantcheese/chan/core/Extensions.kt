package com.github.adamantcheese.chan.core

import android.net.Uri


fun String.extension(): String? {
    val index = this.indexOfLast { ch -> ch == '.' }
    if (index == -1) {
        return null
    }

    if (index == this.lastIndex) {
        // The dot is at the very end of the string, so there is no extension
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

fun Uri.removeLastSegment(): Uri? {
    if (this.pathSegments.size <= 1) {
        // I think we shouldn't return "/" directory here since android won't let us access it anyway
        return null
    }

    val newSegments = this.pathSegments
            .subList(0, pathSegments.lastIndex - 1)

    return Uri.Builder()
            .appendManyEncoded(newSegments)
            .build()
}