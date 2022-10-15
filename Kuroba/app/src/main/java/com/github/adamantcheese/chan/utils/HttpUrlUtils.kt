package com.github.adamantcheese.chan.utils

import okhttp3.HttpUrl
import okhttp3.internal.toImmutableList

fun HttpUrl.trimmedPathSegments(): List<String> {
    val segments: MutableList<String> = pathSegments.toMutableList()
    for (i in segments.indices.reversed()) {
        val segment = segments[i]
        if (segment.isEmpty()) segments.remove(segment)
    }
    return segments.toImmutableList()
}