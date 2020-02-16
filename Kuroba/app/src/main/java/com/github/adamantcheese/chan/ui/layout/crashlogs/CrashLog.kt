package com.github.adamantcheese.chan.ui.layout.crashlogs

import java.io.File

data class CrashLog(val file: File, val fileName: String, var send: Boolean) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CrashLog

        if (fileName != other.fileName) return false
        if (send != other.send) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + send.hashCode()
        return result
    }
}