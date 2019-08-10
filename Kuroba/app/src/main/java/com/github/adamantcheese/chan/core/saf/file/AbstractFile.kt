package com.github.adamantcheese.chan.core.saf.file

abstract class AbstractFile<T>(
        protected val segments: MutableList<Segment> = mutableListOf()
) {
    /**
     * We can't append anything if the last segment's isFileName is true.
     * This is a terminal operation.
     * */
    protected var isFilenameAppended = segments.lastOrNull()?.isFileName ?: false

    /**
     * Appends a new subdirectory to the root directory
     * */
    abstract fun appendSubDirSegment(name: String): T

    /**
     * Appends a file name to the root directory
     * */
    abstract fun appendFileNameSegment(name: String): T

    /**
     * Creates a new file that consists of the root directory and segments (sub dirs or the file name)
     * */
    abstract fun create(): T?

    abstract fun exists(): Boolean
    abstract fun isFile(): Boolean
    abstract fun isDirectory(): Boolean
    abstract fun canRead(): Boolean
    abstract fun canWrite(): Boolean
    abstract fun name(): String?

    fun segmentsCount(): Int = segments.size

    /**
     * Removes the last appended segment if there are any
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

        class DirRoot<T>(holder: T) : Root<T>(holder)
        class FileRoot<T>(holder: T, val fileName: String) : Root<T>(holder)
    }

    /**
     * Segment represents a sub directory or a file name
     * */
    class Segment(
            val name: String,
            val isFileName: Boolean = false
    )
}