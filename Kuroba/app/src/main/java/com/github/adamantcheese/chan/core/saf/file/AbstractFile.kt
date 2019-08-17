package com.github.adamantcheese.chan.core.saf.file

import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

abstract class AbstractFile(
        /**
         * /test/123/test2/filename.txt -> 4 segments
         * */
        protected val segments: MutableList<Segment>
) {
    /**
     * We can't append anything if the last segment's isFileName is true.
     * This is a terminal operation.
     * */
    protected var isFilenameAppended = segments.lastOrNull()?.isFileName ?: false

    /**
     * Appends a new subdirectory to the root directory
     * */
    abstract fun <T : AbstractFile> appendSubDirSegment(name: String): T

    /**
     * Appends a file name to the root directory
     * */
    abstract fun <T : AbstractFile> appendFileNameSegment(name: String): T

    /**
     * Creates a new file that consists of the root directory and segments (sub dirs or the file name)
     * */
    abstract fun <T : AbstractFile> createNew(): T?

    fun <T : AbstractFile> create(): Boolean {
        return createNew<T>() != null
    }

    abstract fun <T> root(): Root<T>
    abstract fun segments(): MutableList<Segment>
    abstract fun exists(): Boolean
    abstract fun isFile(): Boolean
    abstract fun isDirectory(): Boolean
    abstract fun canRead(): Boolean
    abstract fun canWrite(): Boolean
    abstract fun name(): String?
    abstract fun <T : AbstractFile> getParent(): T?
    abstract fun getFullPath(): String
    abstract fun delete(): Boolean
    abstract fun getInputStream(): InputStream?
    abstract fun getOutputStream(): OutputStream?
    abstract fun getName(): String
    abstract fun findFile(fileName: String): DocumentFile?

    fun segmentsCount(): Int = segments.size

    /**
     * Removes the last appended segment if there are any
     * e.g: /test/123/test2 -> /test/123 -> /test
     * */
    fun removeLastSegment(): Boolean {
        if (segments.isEmpty()) {
            return false
        }

        segments.removeAt(segments.lastIndex)
        return true
    }

    /**
     * We can have the root to be a directory or a file.
     * If it's a directory, that means that we can append sub directories to it.
     * If it's a file we can't do that so usually when attempting to append something to the FileRoot
     * an exception will be thrown
     *
     * @param holder either Uri or File. Represents either just a path or a path with file name
     * */
    sealed class Root<T>(val holder: T) {

        fun name(): String? {
            if (this is FileRoot) {
                return this.fileName
            }

            return null
        }

        fun clone(): Root<T> {
            return when (this) {
                is DirRoot<*> -> DirRoot(holder)
                is FileRoot<*> -> FileRoot(holder, fileName)
            }
        }

        /**
         * /test/123/test2
         * or
         * /test/123/test2/5/6/7/8/112233
         *
         * Cannot have an extension!
         * */
        class DirRoot<T>(holder: T) : Root<T>(holder)

        /**
         * /test/123/test2/filename.txt
         * where holder = /test/123/test2/filename.txt (Uri),
         * fileName = filename.txt (may have no extension)
         * */
        class FileRoot<T>(holder: T, val fileName: String) : Root<T>(holder)
    }

    /**
     * Segment represents a sub directory or a file name, e.g:
     * /test/123/test2/filename.txt
     *  ^   ^    ^     ^
     *  |   |    |     +--- File name segment (name = filename.txt, isFileName == true)
     *  +---+----+-- Directory segments (names = [test, 123, test2], isFileName == false)
     * */
    class Segment(
            val name: String,
            val isFileName: Boolean = false
    )
}