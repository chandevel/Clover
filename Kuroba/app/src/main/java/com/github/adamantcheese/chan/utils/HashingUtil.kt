package com.github.adamantcheese.chan.utils

import okio.ByteString.Companion.encodeUtf8
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.InputStream

object HashingUtil {

    fun inputStreamHash(inputStream: InputStream): String {
        return HashingSource.md5(inputStream.source()).use { hashingSource ->
            return@use hashingSource.buffer().use { source ->
                source.readAll(blackholeSink())
                return@use hashingSource.hash.hex()
            }
        }
    }

    fun stringHash(inputString: String): String {
        return inputString.encodeUtf8().md5().hex()
    }

}