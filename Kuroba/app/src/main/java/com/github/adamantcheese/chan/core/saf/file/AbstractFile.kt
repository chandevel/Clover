package com.github.adamantcheese.chan.core.saf.file

import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.core.saf.annotation.ImmutableMethod
import com.github.adamantcheese.chan.core.saf.annotation.MutableMethod
import java.io.*

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
 *
 * Examples.
 *
 * Usually you want to create an [AbstractFile] pointing to some directory (like the Kuroba base dir)
 * and then create either subdirectories or files inside that directory. You can start with one of the
 * following methods:
 *
 * // Creates an [AbstractFile] from base SAF directory. Be aware that the Uri must not be created
 * // manually! This won't work with SAF since one file may have it's Uri changed when Android decides to
 * // do so. Usually you want to call file or directory chooser via SAF API (there are methods for
 * // that in the [FileManager] class) which will return an Uri that you can then pass into fromUri()
 * // method. But usually you don't even need to do this since we usually do this once to get the
 * // Kuroba base directory and then just do our work inside of that directory.
 * AbstractFile baseDir = fileManager.fromUri(Uri.parse(ChanSettings.saveLocationUri.get()));
 *
 * // Creates an [AbstractFile] from base raw directory
 * AbstractFile baseDir = fileManager.fromRawFile(new File(ChanSettings.saveLocation.get()));
 *
 * // Same as above
 * AbstractFile baseDir = fileManager.fromPath(ChanSettings.saveLocation.get());
 *
 *
 * Then you can start appending subdirectories or a filename:
 *
 * // This will create a "test.txt" file located at <Kuroba_Base_Dir>/dir1/dir2/dir3, i.e.
 * // <Kuroba_Base_Dir>/dir1/dir2/dir3/test.txt
 * AbstractFile newFile = baseDir
 *      .appendSubDirSegment("dir1")
 *      .appendSubDirSegment("dir2")
 *      .appendSubDirSegment("dir3")
 *      .appendFileNameSegment("test.txt")
 *      .createNew();
 *
 *  Then you can call methods that are similar to the standard Java File API, e.g. [exists],
 *  [getName], [getLength], [isFile], [isDirectory], [canRead], [canWrite] etc.
 *
 *  If you want to work with multiple files in a directory (or sub directories) you may want
 *  to [clone] the file that represents that directory, e.g:
 *
 *  AbstractFile clonedFile = baseDir.clone();
 *
 *  AbstractFile f1 = clonedFile
 *      .appendFileNameSegment("f1.txt")
 *      .createNew();
 *  AbstractFile f2 = clonedFile
 *      .appendFileNameSegment("f2.txt")
 *      .createNew();
 *  AbstractFile f3 = clonedFile
 *      .appendFileNameSegment("f3.txt")
 *      .createNew();
 *
 *  You have to do this because some methods may mutate the internal state of the [AbstractFile], so
 *  after calling, let's say:
 *
 *  AbstractFile f1 = baseDir
 *      .appendFileNameSegment("f1.txt")
 *      .createNew();
 *
 *  Without cloning it first baseDir will be start pointing to <Kuroba_Base_Dir>/f1.txt instead of
 *  just <Kuroba_Base_Dir>. The same thing applies to any method marked with [MutableMethod] annotation.
 *  methods marked with [ImmutableMethod] do this stuff internally so they are safe to use without
 *  cloning.
 *
 *  Sometimes you don't know which external directory to choose to store a new file (the SAF or the
 *  old raw Java File external directory). In this case you can use:
 *
 *  AbstractFile baseDir = fileManager.newSaveLocationFile();
 *
 *  Method which will create an [AbstractFile] with root pointing to either Kuroba SAF base directory
 *  (if user has set it) or if he didn't then to the default external directory (Backed by raw
 *  Java File) or
 *
 *  AbstractFile baseDir = fileManager.newLocalThreadFile();
 *
 *  Method which will create an [AbstractFile] with root pointing to either user's selected local
 *  threads directory or to the default external local threads directory
 *
 * */
abstract class AbstractFile(
        /**
         * /test/123/test2/filename.txt -> 4 segments
         * */
        protected val segments: MutableList<Segment>
) {
    /**
     * Appends a new subdirectory (or couple of subdirectories, e.g. "dir1/dir2/dir3")
     * to the root directory
     * */
    @MutableMethod
    abstract fun appendSubDirSegment(name: String): AbstractFile

    /**
     * Appends a file name to the root directory (or couple subdirectories with filename at the end,
     * e.g. "dir1/dir2/dir3/test.txt"
     * */
    @MutableMethod
    abstract fun appendFileNameSegment(name: String): AbstractFile

    /**
     * Creates a new file that consists of the root directory and segments (sub dirs or the file name)
     * Behave similarly to Java's mkdirs() method but work not only with directories but files as well.
     * */
    @ImmutableMethod
    abstract fun createNew(): AbstractFile?

    @ImmutableMethod
    fun create(): Boolean {
        return createNew() != null
    }

    /**
     * When doing something with an [AbstractFile] (like appending a subdir or a filename) the
     * [AbstractFile] will change because it's mutable. So if you don't want to change the original
     * [AbstractFile] you need to make a copy via this method (like, if you want to search for
     * a couple of files in the same directory you would want to clone the directory
     * [AbstractFile] and then append the filename to those copies)
     * */
    abstract fun clone(): AbstractFile

    @ImmutableMethod
    abstract fun exists(): Boolean

    @ImmutableMethod
    abstract fun isFile(): Boolean

    @ImmutableMethod
    abstract fun isDirectory(): Boolean

    @ImmutableMethod
    abstract fun canRead(): Boolean

    @ImmutableMethod
    abstract fun canWrite(): Boolean

    @MutableMethod
    abstract fun getParent(): AbstractFile?

    @ImmutableMethod
    abstract fun getFullPath(): String

    @ImmutableMethod
    abstract fun delete(): Boolean

    @ImmutableMethod
    abstract fun getInputStream(): InputStream?

    @ImmutableMethod
    abstract fun getOutputStream(): OutputStream?

    @ImmutableMethod
    abstract fun getName(): String

    @ImmutableMethod
    abstract fun findFile(fileName: String): AbstractFile?

    @ImmutableMethod
    abstract fun getLength(): Long

    @ImmutableMethod
    abstract fun <T> withFileDescriptor(
            fileDescriptorMode: FileDescriptorMode,
            func: (FileDescriptor) -> T): Result<T>

    @ImmutableMethod
    abstract fun listFiles(): List<AbstractFile>

    @ImmutableMethod
    abstract fun lastModified(): Long

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


    protected fun appendSubDirSegmentInner(name: String): AbstractFile {
        check(!isFilenameAppended()) { "Cannot append anything after file name has been appended" }
        require(!name.isBlank()) { "Bad name: $name" }
        require(name.extension() == null) {
            "Directory name must not contain extension, extension = ${name.extension()}"
        }

        val nameList = if (name.contains(File.separatorChar)) {
            name.split(File.separatorChar)
        } else {
            listOf(name)
        }

        nameList
                .onEach { splitName ->
                    require(splitName.extension() == null) {
                        "appendSubDirSegment does not allow segments with extensions! " +
                                "bad name = $splitName"
                    }
                }
                .map { splitName -> Segment(splitName) }
                .forEach { segment -> segments += segment }

        return this
    }

    protected fun appendFileNameSegmentInner(name: String): AbstractFile {
        check(!isFilenameAppended()) { "Cannot append anything after file name has been appended" }
        require(!name.isBlank()) { "Bad name: $name" }

        val nameList = if (name.contains(File.separatorChar)) {
            val split = name.split(File.separatorChar)
            check(split.size >= 2) { "Should have at least two entries, name = $name" }

            split
        } else {
            listOf(name)
        }

        require(nameList.last().extension() != null) { "Last segment must be a filename" }

        for ((index, splitName) in nameList.withIndex()) {
            require(!(splitName.extension() != null && index != nameList.lastIndex)) {
                "Only the last split segment may have a file name, " +
                        "bad segment index = ${index}/${nameList.lastIndex}, bad name = $splitName"
            }

            val isFileName = index == nameList.lastIndex
            segments += Segment(splitName, isFileName)
        }

        return this
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