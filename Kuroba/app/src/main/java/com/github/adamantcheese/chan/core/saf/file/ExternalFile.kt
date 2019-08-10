package com.github.adamantcheese.chan.core.saf.file

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendManyEncoded
import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.utils.Logger

class ExternalFile(
        private val appContext: Context,
        private val root: Root<Uri>
) : AbstractFile<ExternalFile>() {
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    override fun appendSubDirSegment(name: String): ExternalFile {
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

    override fun appendFileNameSegment(name: String): ExternalFile {
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

    override fun create(): ExternalFile? {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        val rootDir = DocumentFile.fromTreeUri(appContext, root.holder)
        if (rootDir == null) {
            // Couldn't create a DocumentFile from the root
            Logger.e(TAG, "DocumentFile.fromTreeUri returned null, root.uri = ${root.holder}")
            return null
        }

        if (segments.isEmpty()) {
            // Root is probably already exists and there is no point in creating it again so just
            // return null here
            Logger.e(TAG, "No segments")
            return null
        }

        var newFile: DocumentFile? = null

        for (segment in segments) {
            val file = newFile ?: rootDir

            val prevFile = file.findFile(segment.name)
            if (prevFile != null) {
                // File already exists, no need to create it again (and we won't be able)
                newFile = prevFile
                continue
            }

            if (!segment.isFileName) {
                newFile = file.createDirectory(segment.name)
                if (newFile == null) {
                    Logger.e(TAG, "file.createDirectory returned null, file.uri = ${file.uri}, " +
                            "segment.name = ${segment.name}")
                    return null
                }
            } else {
                newFile = file.createFile(getMimeType(segment.name), segment.name)
                if (newFile == null) {
                    Logger.e(TAG, "file.createFile returned null, file.uri = ${file.uri}, " +
                            "segment.name = ${segment.name}")
                    return null
                }

                // Ignore any left segments (which we shouldn't have) after encountering fileName
                // segment
                return ExternalFile(appContext, Root.FileRoot(newFile.uri, segment.name))
            }
        }

        if (newFile == null) {
            Logger.e(TAG, "result file is null")
            return null
        }

        return ExternalFile(appContext, Root.DirRoot(newFile.uri))
    }

    override fun exists(): Boolean = toDocumentFile()?.exists() ?: false
    override fun isFile(): Boolean = toDocumentFile()?.isFile ?: false
    override fun isDirectory(): Boolean = toDocumentFile()?.isDirectory ?: false
    override fun canRead(): Boolean = toDocumentFile()?.canRead() ?: false
    override fun canWrite(): Boolean = toDocumentFile()?.canWrite() ?: false
    override fun name(): String? = root.name()

    private fun toDocumentFile(): DocumentFile? {
        return if (segments.isEmpty()) {
            when (root) {
                is Root.DirRoot -> DocumentFile.fromTreeUri(appContext, root.holder)
                is Root.FileRoot -> DocumentFile.fromSingleUri(appContext, root.holder)
            }
        } else {
            val fullUri = root.holder
                    .buildUpon()
                    .appendManyEncoded(segments.map { segment -> segment.name })
                    .build()

            when (root) {
                is Root.DirRoot -> DocumentFile.fromTreeUri(appContext, fullUri)
                is Root.FileRoot -> DocumentFile.fromSingleUri(appContext, fullUri)
            }
        }
    }

    private fun getMimeType(filename: String): String {
        val extension = filename.extension()
        if (extension == null) {
            return BINARY_FILE_MIME_TYPE
        }

        val mimeType = mimeTypeMap.getMimeTypeFromExtension(extension)
        if (mimeType == null || mimeType.isEmpty()) {
            return BINARY_FILE_MIME_TYPE
        }

        return mimeType
    }

    companion object {
        private const val TAG = "FileManager"
        private const val BINARY_FILE_MIME_TYPE = "application/octet-stream"
    }
}