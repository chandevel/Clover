package com.github.adamantcheese.chan.core.saf.file

import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendMany
import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.utils.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class RawFile(
        private val root: Root<File>
) : AbstractFile() {

    override fun <T : AbstractFile>  appendSubDirSegment(name: String): T {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (isFilenameAppended) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        if (name.extension() != null) {
            throw IllegalArgumentException("Directory name must not contain extension, " +
                    "extension = ${name.extension()}")
        }

        segments += Segment(name)
        return this as T
    }

    override fun <T : AbstractFile>  appendFileNameSegment(name: String): T {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (isFilenameAppended) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        segments += Segment(name, true)
        return this as T
    }

    override fun <T : AbstractFile>  createNew(): T? {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (segments.isEmpty()) {
            // Root is probably already existing and there is no point in creating it again so just
            // return null here
            Logger.e(TAG, "No segments")
            return null
        }

        var newFile = root.holder

        for (segment in segments) {
            if (!segment.isFileName) {
                newFile = File(newFile, segment.name)
            } else {
                return RawFile(Root.FileRoot(File(newFile, segment.name), segment.name)) as T
            }
        }

        return RawFile(Root.DirRoot(newFile)) as T
    }

    override fun exists(): Boolean = toFile().exists()
    override fun isFile(): Boolean = toFile().isFile
    override fun isDirectory(): Boolean = toFile().isDirectory
    override fun canRead(): Boolean = toFile().canRead()
    override fun canWrite(): Boolean = toFile().canWrite()
    override fun name(): String? = root.name()

    override fun <T : AbstractFile>  getParent(): T? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this as T
        }

        return RawFile(Root.DirRoot(root.holder.parentFile)) as T
    }

    override fun getFullPath(): String {
        return root.holder
                .appendMany(segments.map { segment -> segment.name })
                .absolutePath
    }

    override fun delete(): Boolean {
        return toFile().delete()
    }

    override fun getInputStream(): InputStream? {
        val file = toFile()

        if (!file.exists()) {
            Logger.e(TAG, "getInputStream() file does not exist, path = ${file.absolutePath}")
            return null
        }

        if (!file.isFile) {
            Logger.e(TAG, "getInputStream() file is not a file, path = ${file.absolutePath}")
            return null
        }

        if (!file.canRead()) {
            Logger.e(TAG, "getInputStream() cannot read from file, path = ${file.absolutePath}")
            return null
        }

        return file.inputStream()
    }

    override fun getOutputStream(): OutputStream? {
        val file = toFile()

        if (!file.exists()) {
            Logger.e(TAG, "getOutputStream() file does not exist, path = ${file.absolutePath}")
            return null
        }

        if (!file.isFile) {
            Logger.e(TAG, "getOutputStream() file is not a file, path = ${file.absolutePath}")
            return null
        }

        if (!file.canWrite()) {
            Logger.e(TAG, "getOutputStream() cannot write to file, path = ${file.absolutePath}")
            return null
        }

        return file.outputStream()
    }

    override fun <T> getFullRoot(): Root<T> {
        return if (segments.isEmpty()) {
            root as Root<T>
        } else {
            var newFile = File(root.holder.absolutePath)

            for (segment in segments) {
                newFile = File(newFile, segment.name)
            }

            val lastSegment = segments.last()
            if (lastSegment.isFileName) {
                return Root.FileRoot(newFile, lastSegment.name) as Root<T>
            }

            return Root.DirRoot(newFile) as Root<T>
        }
    }

    override fun getName(): String {
        return toFile().name
    }

    override fun findFile(fileName: String): DocumentFile? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun toFile(): File {
        return if (segments.isEmpty()) {
            root.holder
        } else {
            root.holder.appendMany(segments.map { segment -> segment.name })
        }
    }

    companion object {
        private const val TAG = "RawFile"
    }
}