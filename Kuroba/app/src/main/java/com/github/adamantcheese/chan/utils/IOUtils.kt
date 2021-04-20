/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.utils

import android.content.Context
import java.io.File
import java.io.InputStream

object IOUtils {
    @JvmStatic
    fun assetAsString(context: Context, assetName: String): String {
        return context.resources.assets.open(assetName).use { inputStream ->
            readString(inputStream)
        }
    }

    @JvmStatic
    fun readString(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { text -> text.readText() }
    }

    /**
     * inputStream must be closed by the caller! Use a try-with-resources block, or a use block if Kotlin
     *
     * @param maxBytes -1 if you don't want to check this
     */
    @JvmStatic
    fun writeToFile(inputStream: InputStream, file: File, maxBytes: Long) {
        if (maxBytes > 0 && inputStream.available() > maxBytes) throw Exception("File too large")
        file.outputStream().buffered().use { fileOutputStream -> inputStream.buffered().copyTo(fileOutputStream) }
    }
}