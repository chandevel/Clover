package com.github.adamantcheese.chan.core.saf.file

import com.github.adamantcheese.chan.core.appendMany
import com.github.adamantcheese.chan.utils.Logger
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class RawFile(
        private val root: Root<File>,
        segments: MutableList<Segment> = mutableListOf()
) : AbstractFile(segments) {

    override fun <T : AbstractFile> appendSubDirSegment(name: String): T {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendSubDirSegmentInner(name)
    }

    override fun <T : AbstractFile> appendFileNameSegment(name: String): T {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendFileNameSegmentInner(name)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : AbstractFile>  createNew(): T? {
        check(root !is Root.FileRoot) {
            // TODO: do we need this check?
            "root is already FileRoot, cannot append anything anymore"
        }

        if (segments.isEmpty()) {
            // Root is probably already existing and there is no point in creating it again so just
            // return null here
            Logger.e(TAG, "No segments")
            return null
        }

        var newFile = root.holder

        for (segment in segments) {
            newFile = File(newFile, segment.name)

            if (segment.isFileName) {
                if (!newFile.exists() && !newFile.createNewFile()) {
                    Logger.e(TAG, "Could not create a new file, path = " + newFile.absolutePath)
                    return null
                }
            } else {
                if (!newFile.exists() && !newFile.mkdir()) {
                    Logger.e(TAG, "Could not create a new directory, path = " + newFile.absolutePath)
                    return null
                }
            }

            if (segment.isFileName) {
                return RawFile(Root.FileRoot(newFile, segment.name)) as T
            }
        }

        return RawFile(Root.DirRoot(newFile)) as T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : AbstractFile> clone(): T = RawFile(
            root.clone(),
            segments.toMutableList()) as T

    override fun exists(): Boolean = toFile().exists()
    override fun isFile(): Boolean = toFile().isFile
    override fun isDirectory(): Boolean = toFile().isDirectory
    override fun canRead(): Boolean = toFile().canRead()
    override fun canWrite(): Boolean = toFile().canWrite()

    @Suppress("UNCHECKED_CAST")
    override fun <T : AbstractFile>  getParent(): T? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this as T
        }

        return RawFile(Root.DirRoot(root.holder.parentFile)) as T
    }

    override fun getFullPath(): String {
        return File(root.holder.absolutePath)
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

    override fun getName(): String {
        return toFile().name
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T: AbstractFile> findFile(fileName: String): T? {
        check(root !is Root.FileRoot) { "Cannot use FileRoot as directory" }

        val copy = File(root.holder.absolutePath)
        if (segments.isNotEmpty()) {
            copy.appendMany(segments.map { segment -> segment.name })
        }

        val resultFile = File(copy.absolutePath, fileName)
        if (!resultFile.exists()) {
            return null
        }

        val newRoot = if (resultFile.isFile) {
            Root.FileRoot(resultFile, resultFile.name)
        } else {
            Root.DirRoot(resultFile)
        }

        return RawFile(newRoot) as T
    }

    override fun getLength(): Long = toFile().length()

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