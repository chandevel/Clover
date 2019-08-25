package com.github.adamantcheese.chan.core

import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

private const val BINARY_FILE_MIME_TYPE = "application/octet-stream"

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
        this.appendPath(segment)
    }

    return this
}

fun Uri.removeLastSegment(): Uri? {
    if (this.pathSegments.size <= 1) {
        // I think we shouldn't return "/" directory here since android won't let us access it anyway
        return null
    }

    val newSegments = this.pathSegments
            .subList(0, pathSegments.lastIndex)

    return Uri.Builder()
            .appendManyEncoded(newSegments)
            .build()
}

fun MimeTypeMap.getMimeFromFilename(filename: String): String {
    val extension = filename.extension()
    if (extension == null) {
        return BINARY_FILE_MIME_TYPE
    }

    val mimeType = this.getMimeTypeFromExtension(extension)
    if (mimeType == null || mimeType.isEmpty()) {
        return BINARY_FILE_MIME_TYPE
    }

    return mimeType
}

fun File.appendMany(segments: List<String>): File {
    var newFile = File(this.absolutePath)

    for (segment in segments) {
        newFile = File(newFile, segment)
    }

    return newFile
}

fun Int.toCharArray(): CharArray {
    val charArray = CharArray(4)

    charArray[0] = ((this shr 24) and 0x000000FF).toChar()
    charArray[1] = ((this shr 16) and 0x000000FF).toChar()
    charArray[2] = ((this shr 8) and 0x000000FF).toChar()
    charArray[3] = ((this) and 0x000000FF).toChar()

    return charArray
}

fun CharArray.toInt(): Int {
    check(this.size == 4) { "CharArray must have length of exactly 4 bytes" }

    var value: Int = 0
    value = value or (this[0].toInt() shl 24)
    value = value or (this[1].toInt() shl 16)
    value = value or (this[2].toInt() shl 8)
    value = value or (this[3].toInt())

    return value
}