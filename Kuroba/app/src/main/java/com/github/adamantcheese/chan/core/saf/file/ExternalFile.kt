package com.github.adamantcheese.chan.core.saf.file

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendManyEncoded
import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.core.getMimeFromFilename
import com.github.adamantcheese.chan.core.removeLastSegment
import com.github.adamantcheese.chan.utils.Logger
import java.io.FileDescriptor

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
                newFile = file.createFile(mimeTypeMap.getMimeFromFilename(segment.name), segment.name)
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

    override fun getParent(): ExternalFile? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this
        }

        val newUri = root.holder.removeLastSegment()
        if (newUri == null) {
            Logger.e(TAG, "getParent() removeLastSegment() returned null")
            return null
        }

        return ExternalFile(appContext, Root.DirRoot(newUri))
    }

    fun getParcelFileDescriptor(fileDescriptorMode: FileDescriptorMode): ParcelFileDescriptor? {
        return appContext.contentResolver.openFileDescriptor(
                root.holder,
                fileDescriptorMode.mode)
    }

    /**
     * Example:
     * withFileDescriptor(FileDescriptorMode.Read) { fd ->
     *      // Do anything here with FileDescriptor here, it will be closed automatically upon
     *      // exiting the lambda
     * }
     * */
    fun withFileDescriptor(
            fileDescriptorMode: FileDescriptorMode,
            func: (FileDescriptor) -> Unit
    ): Boolean {
        return getParcelFileDescriptor(fileDescriptorMode)?.use { pfd ->
            func(pfd.fileDescriptor)
            return@use true
        } ?: false
    }

    override fun getFullPath(): String {
        return Uri.parse(root.holder.toString()).buildUpon()
                .appendManyEncoded(segments.map { segment -> segment.name })
                .build()
                .toString()
    }

    private fun toDocumentFile(): DocumentFile? {
        val uri = if (segments.isEmpty()) {
            root.holder
        } else {
            root.holder
                    .buildUpon()
                    .appendManyEncoded(segments.map { segment -> segment.name })
                    .build()
        }

        return when (root) {
            is Root.DirRoot -> DocumentFile.fromTreeUri(appContext, uri)
            is Root.FileRoot -> DocumentFile.fromSingleUri(appContext, uri)
        }
    }

    enum class FileDescriptorMode(val mode: String) {
        Read("r"),
        Write("w"),
        // It is recommended to prefer either Read or Write modes in the documentation.
        // Use ReadWrite only when it is really necessary.
        ReadWrite("rw")
    }

    companion object {
        private const val TAG = "FileManager"
    }
}