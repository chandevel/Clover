package com.github.adamantcheese.chan.core.saf.file

import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.core.saf.annotation.ImmutableMethod
import com.github.adamantcheese.chan.core.saf.annotation.MutableMethod
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * An abstraction class over both the Java File and the new Storage Access Framework DocumentFile.
 *
 * Some methods are marked with [MutableMethod] annotation. This means that such method are gonna
 * mutate the inner data of the [AbstractFile] (such as root or segments). Sometimes this behavior is
 * not desirable. For example, when you have an AbstractFile representing some directory that may
 * not even exists on the disk and you want to check whether it exists and if it does check some
 * additional files inside that directory. In such case you may want to preserve the [AbstractFile]
 * that represents that directory in it's original state. To do this you have to call the [clone]
 * method on the file that represents the directory. It will create a copy of the file that you can
 * safely work without worry that the original file may change.
 *
 * Other methods are marked with [ImmutableMethod] annotation. This means that those files create a
 * copy of the [AbstractFile] internally and are safe to use without calling [clone]
 * */
abstract class AbstractFile(
        /**
         * /test/123/test2/filename.txt -> 4 segments
         * */
        protected val segments: MutableList<Segment>
) {

    /**
     * Appends a new subdirectory to the root directory
     * */
    @MutableMethod
    abstract fun <T : AbstractFile> appendSubDirSegment(name: String): T

    /**
     * Appends a file name to the root directory
     * */
    @MutableMethod
    abstract fun <T : AbstractFile> appendFileNameSegment(name: String): T

    /**
     * Creates a new file that consists of the root directory and segments (sub dirs or the file name)
     * Behave similarly to Java's mkdirs() method but work not only with directories but files as well.
     * */
    @ImmutableMethod
    abstract fun <T : AbstractFile> createNew(): T?

    @ImmutableMethod
    fun <T : AbstractFile> create(): Boolean {
        return createNew<T>() != null
    }

    /**
     * When doing something with an AbstractFile (like appending a subdir or a filename) the
     * AbstractFile will change because it's mutable. So if you don't want to change the original
     * AbstractFile you need to make a copy via this method (like, if you want to search for
     * a couple of files in the same directory you would want to clone the directory
     * AbstractFile and then append the filename to those copies)
     * */
    abstract fun <T : AbstractFile> clone(): T

    @MutableMethod
    abstract fun exists(): Boolean

    @MutableMethod
    abstract fun isFile(): Boolean

    @MutableMethod
    abstract fun isDirectory(): Boolean

    @MutableMethod
    abstract fun canRead(): Boolean

    @MutableMethod
    abstract fun canWrite(): Boolean

    @MutableMethod
    abstract fun <T : AbstractFile> getParent(): T?

    @ImmutableMethod
    abstract fun getFullPath(): String

    @MutableMethod
    abstract fun delete(): Boolean

    @MutableMethod
    abstract fun getInputStream(): InputStream?

    @MutableMethod
    abstract fun getOutputStream(): OutputStream?

    @MutableMethod
    abstract fun getName(): String

    @ImmutableMethod
    abstract fun <T: AbstractFile> findFile(fileName: String): T?

    /**
     * Removes the last appended segment if there are any
     * e.g: /test/123/test2 -> /test/123 -> /test
     * */
    @MutableMethod
    fun removeLastSegment(): Boolean {
        if (segments.isEmpty()) {
            return false
        }

        segments.removeAt(segments.lastIndex)
        return true
    }

    protected fun <T : AbstractFile> appendSubDirSegmentInner(name: String): T {
        if (isFilenameAppended()) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        if (name.extension() != null) {
            throw IllegalArgumentException("Directory name must not contain extension, " +
                    "extension = ${name.extension()}")
        }

        val nameList = if (name.contains(File.separatorChar)) {
            name.split(File.separatorChar)
        } else {
            listOf(name)
        }

        nameList
                .onEach { splitName ->
                    if (splitName.extension() != null) {
                        throw IllegalArgumentException("appendSubDirSegment does not allow segments " +
                                "with extensions! bad name = $splitName")
                    }
                }
                .map { splitName -> Segment(splitName) }
                .forEach { segment -> segments += segment }

        return this as T
    }

    protected fun <T : AbstractFile> appendFileNameSegmentInner(name: String): T {
        if (isFilenameAppended()) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        val nameList = if (name.contains(File.separatorChar)) {
            val split = name.split(File.separatorChar)
            if (split.size < 2) {
                throw IllegalStateException("Should have at least two entries, name = $name")
            }

            split
        } else {
            listOf(name)
        }

        for ((index, splitName) in nameList.withIndex()) {
            if (splitName.extension() != null && index != nameList.lastIndex) {
                throw IllegalArgumentException("Only the last split segment may have a file name, " +
                        "bad segment index = ${index}/${nameList.lastIndex}, bad name = $splitName")
            }

            val isFileName = index == nameList.lastIndex
            segments += Segment(splitName, isFileName)
        }

        return this as T
    }

    private fun isFilenameAppended(): Boolean = segments.lastOrNull()?.isFileName ?: false

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