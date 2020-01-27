package com.github.adamantcheese.chan.utils

import okhttp3.internal.and


object ConversionUtils {

    @JvmStatic
    fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
                (value ushr 24).toByte(),
                (value ushr 16).toByte(),
                (value ushr 8).toByte(),
                value.toByte()
        )
    }

    @JvmStatic
    fun intToCharArray(value: Int): CharArray {
        return charArrayOf(
                (value ushr 24).toChar(),
                (value ushr 16).toChar(),
                (value ushr 8).toChar(),
                value.toChar()
        )
    }

    @JvmStatic
    fun byteArrayToInt(bytes: ByteArray): Int {
        return (bytes[0] and 0xFF) shl 24 or
                ((bytes[1] and 0xFF) shl 16) or
                ((bytes[2] and 0xFF) shl 8) or
                ((bytes[3] and 0xFF) shl 0)
    }

    @JvmStatic
    fun charArrayToInt(bytes: CharArray): Int {
        return (bytes[0].toByte() and 0xFF) shl 24 or
                ((bytes[1].toByte() and 0xFF) shl 16) or
                ((bytes[2].toByte() and 0xFF) shl 8) or
                ((bytes[3].toByte() and 0xFF) shl 0)
    }

}