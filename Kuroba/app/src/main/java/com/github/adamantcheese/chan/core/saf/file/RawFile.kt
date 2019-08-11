package com.github.adamantcheese.chan.core.saf.file

import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.utils.Logger
import java.io.File
import java.lang.IllegalStateException

class RawFile(
        private val root: Root<File>
) : AbstractFile<RawFile>() {

    override fun appendSubDirSegment(name: String): RawFile {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (isFilenameAppended) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isNullOrBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        if (name.extension() != null) {
            throw IllegalArgumentException("Directory name must not contain extension, " +
                    "extension = ${name.extension()}")
        }

        segments += Segment(name)
        return this
    }

    override fun appendFileNameSegment(name: String): RawFile {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (isFilenameAppended) {
            throw IllegalStateException("Cannot append anything after file name has been appended")
        }

        if (name.isNullOrBlank()) {
            throw IllegalArgumentException("Bad name: $name")
        }

        segments += Segment(name, true)
        return this
    }

    override fun create(): RawFile? {
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
                return RawFile(Root.FileRoot(File(newFile, segment.name), segment.name))
            }
        }

        return RawFile(Root.DirRoot(newFile))
    }

    override fun exists(): Boolean = toFile().exists()
    override fun isFile(): Boolean = toFile().isFile
    override fun isDirectory(): Boolean = toFile().isDirectory
    override fun canRead(): Boolean = toFile().canRead()
    override fun canWrite(): Boolean = toFile().canWrite()
    override fun name(): String? = root.name()

    override fun getParent(): RawFile? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this
        }

        return RawFile(Root.DirRoot(root.holder.parentFile))
    }

    private fun toFile(): File {
        val uri = if (segments.isEmpty()) {
            root.holder
        } else {
            var newFile = root.holder

            for (segment in segments) {
                newFile = File(newFile, segment.name)
            }

            newFile
        }

        return when (root) {
            is Root.DirRoot -> uri
            is Root.FileRoot -> uri
        }
    }

    companion object {
        private const val TAG = "RawFile"
    }
}