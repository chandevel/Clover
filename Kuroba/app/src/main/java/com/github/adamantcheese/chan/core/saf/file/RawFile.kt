package com.github.adamantcheese.chan.core.saf.file

import com.github.adamantcheese.chan.core.appendMany
import com.github.adamantcheese.chan.utils.Logger
import java.io.*

class RawFile(
        private val root: Root<File>,
        segments: MutableList<Segment> = mutableListOf()
) : AbstractFile(segments) {

    override fun appendSubDirSegment(name: String): RawFile {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendSubDirSegmentInner(name) as RawFile
    }

    override fun appendFileNameSegment(name: String): RawFile {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendFileNameSegmentInner(name) as RawFile
    }

    override fun createNew(): RawFile? {
        check(root !is Root.FileRoot) {
            "root is already FileRoot, cannot append anything anymore"
        }

        if (segments.isEmpty()) {
            if (!root.holder.exists()) {
                if (root.holder.isFile) {
                    if (!root.holder.createNewFile()) {
                        Logger.e(TAG, "Couldn't create file, path = ${root.holder.absolutePath}")
                        return null
                    }
                } else {
                    if (!root.holder.mkdirs()) {
                        Logger.e(TAG, "Couldn't create directory, path = ${root.holder.absolutePath}")
                        return null
                    }
                }
            }

            return this
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
                return RawFile(Root.FileRoot(newFile, segment.name))
            }
        }

        return RawFile(Root.DirRoot(newFile))
    }

    override fun clone(): RawFile = RawFile(
            root.clone(),
            segments.toMutableList())

    override fun exists(): Boolean = clone().toFile().exists()
    override fun isFile(): Boolean = clone().toFile().isFile
    override fun isDirectory(): Boolean = clone().toFile().isDirectory
    override fun canRead(): Boolean = clone().toFile().canRead()
    override fun canWrite(): Boolean = clone().toFile().canWrite()

    override fun getFullPath(): String {
        return File(root.holder.absolutePath)
                .appendMany(segments.map { segment -> segment.name })
                .absolutePath
    }

    override fun delete(): Boolean {
        return clone().toFile().delete()
    }

    override fun getInputStream(): InputStream? {
        val file = clone().toFile()

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
        val file = clone().toFile()

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
        return clone().toFile().name
    }

    override fun findFile(fileName: String): RawFile? {
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

        return RawFile(newRoot)
    }

    override fun getLength(): Long = clone().toFile().length()

    override fun <T> withFileDescriptor(
            fileDescriptorMode: FileDescriptorMode,
            func: (FileDescriptor) -> T): Result<T> {
        return runCatching {
            val fileCopy = clone().toFile()

            when (fileDescriptorMode) {
                FileDescriptorMode.Read -> FileInputStream(fileCopy).use { fis -> func(fis.fd) }
                FileDescriptorMode.Write,
                FileDescriptorMode.WriteTruncate -> {
                    val fileOutputStream = when (fileDescriptorMode) {
                        FileDescriptorMode.Write -> FileOutputStream(fileCopy, false)
                        FileDescriptorMode.WriteTruncate -> FileOutputStream(fileCopy, true)
                        else -> throw NotImplementedError("Not implemented for " +
                                "fileDescriptorMode = ${fileDescriptorMode.name}")
                    }

                    fileOutputStream.use { fos -> func(fos.fd) }
                }
                else -> throw NotImplementedError("Not implemented for " +
                        "fileDescriptorMode = ${fileDescriptorMode.name}")
            }
        }
    }

    override fun lastModified(): Long {
        return clone().toFile().lastModified()
    }

    override fun listFiles(): List<RawFile> {
        check(root !is Root.FileRoot) { "Cannot use listFiles with FileRoot" }

        return clone()
                .toFile()
                .listFiles()
                .map { file -> RawFile(Root.DirRoot(file)) }
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