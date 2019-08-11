package com.github.adamantcheese.chan.core.saf.file

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendManyEncoded
import com.github.adamantcheese.chan.core.extension
import com.github.adamantcheese.chan.core.getMimeFromFilename
import com.github.adamantcheese.chan.utils.Logger
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream

class ExternalFile(
        private val appContext: Context,
        private val root: Root<Uri>
) : AbstractFile() {
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    override fun <T : AbstractFile> appendSubDirSegment(name: String): T {
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
        return this as T
    }

    override fun <T : AbstractFile> appendFileNameSegment(name: String): T {
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
        return this as T
    }

    override fun <T : AbstractFile> createNew(): T? {
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
                return ExternalFile(appContext, Root.FileRoot(newFile.uri, segment.name)) as T
            }
        }

        if (newFile == null) {
            Logger.e(TAG, "result file is null")
            return null
        }

        return ExternalFile(appContext, Root.DirRoot(newFile.uri)) as T
    }

    override fun exists(): Boolean = toDocumentFile()?.exists() ?: false
    override fun isFile(): Boolean = toDocumentFile()?.isFile ?: false
    override fun isDirectory(): Boolean = toDocumentFile()?.isDirectory ?: false
    override fun canRead(): Boolean = toDocumentFile()?.canRead() ?: false
    override fun canWrite(): Boolean = toDocumentFile()?.canWrite() ?: false
    override fun name(): String? = root.name()

    override fun <T : AbstractFile> getParent(): T? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this as T
        }

        val parentUri = when (root) {
            is Root.DirRoot -> DocumentFile.fromTreeUri(appContext, root.holder)?.parentFile?.uri
            is Root.FileRoot -> DocumentFile.fromSingleUri(appContext, root.holder)?.parentFile?.uri
        }

        if (parentUri == null) {
            Logger.e(TAG, "getParent() parentUri == null")
            return null
        }

        return ExternalFile(appContext, Root.DirRoot(parentUri)) as T
    }

    override fun getFullPath(): String {
        return Uri.parse(root.holder.toString()).buildUpon()
                .appendManyEncoded(segments.map { segment -> segment.name })
                .build()
                .toString()
    }

    override fun delete(): Boolean {
        return toDocumentFile()?.delete() ?: false
    }

    override fun getInputStream(): InputStream? {
        val contentResolver = appContext.contentResolver
        val documentFile = toDocumentFile()

        if (documentFile == null) {
            Logger.e(TAG, "getInputStream() toDocumentFile() returned null")
            return null
        }

        if (!documentFile.exists()) {
            Logger.e(TAG, "getInputStream() documentFile does not exist, uri = ${documentFile.uri}")
            return null
        }

        if (!documentFile.isFile) {
            Logger.e(TAG, "getInputStream() documentFile is not a file, uri = ${documentFile.uri}")
            return null
        }

        if (!documentFile.canRead()) {
            Logger.e(TAG, "getInputStream() cannot read from documentFile, uri = ${documentFile.uri}")
            return null
        }

        return contentResolver.openInputStream(documentFile.uri)
    }

    override fun getOutputStream(): OutputStream? {
        val contentResolver = appContext.contentResolver
        val documentFile = toDocumentFile()

        if (documentFile == null) {
            Logger.e(TAG, "getOutputStream() toDocumentFile() returned null")
            return null
        }

        if (!documentFile.exists()) {
            Logger.e(TAG, "getOutputStream() documentFile does not exist, uri = ${documentFile.uri}")
            return null
        }

        if (!documentFile.isFile) {
            Logger.e(TAG, "getOutputStream() documentFile is not a file, uri = ${documentFile.uri}")
            return null
        }

        if (!documentFile.canWrite()) {
            Logger.e(TAG, "getOutputStream() cannot write to documentFile, uri = ${documentFile.uri}")
            return null
        }

        return contentResolver.openOutputStream(documentFile.uri)
    }

    override fun <T> getFullRoot(): Root<T> {
        return if (segments.isEmpty()) {
            root as Root<T>
        } else {
            val uriBuilder = root.holder.buildUpon()

            for (segment in segments) {
                uriBuilder.appendEncodedPath(segment.name)
            }

            val lastSegment = segments.last()
            if (lastSegment.isFileName) {
                return Root.FileRoot(
                        uriBuilder.build() as T,
                        lastSegment.name)
            }

            return Root.DirRoot(uriBuilder.build() as T)
        }
    }

    override fun getName(): String {
        if (segments.isNotEmpty() && segments.last().isFileName) {
            return segments.last().name
        }

        val documentFile = toDocumentFile()
        if (documentFile == null) {
            throw IllegalStateException("getName() toDocumentFile() returned null")
        }

        return documentFile.name
                ?:throw IllegalStateException("Could not extract file name from document file")
    }

    fun getParcelFileDescriptor(fileDescriptorMode: FileDescriptorMode): ParcelFileDescriptor? {
        return appContext.contentResolver.openFileDescriptor(
                root.holder,
                fileDescriptorMode.mode)
    }

    /**
     * An extension function that allows to do something with a FileDescriptor without having
     * to worry about not closing it in the end.
     *
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

    private fun toDocumentFile(): DocumentFile? {
        val uri = if (segments.isEmpty()) {
            root.holder
        } else {
            root.holder
                    .buildUpon()
                    .appendManyEncoded(segments.map { segment -> segment.name })
                    .build()
        }

        // If there are no segments check whether the root is a directory or a file
        return if (segments.isEmpty()) {
            when (root) {
                is Root.DirRoot -> DocumentFile.fromTreeUri(appContext, uri)
                is Root.FileRoot -> DocumentFile.fromSingleUri(appContext, uri)
            }
        } else {
            // Otherwise if there are segments check whether the last segment is isFileName or not
            if (!segments.last().isFileName) {
                DocumentFile.fromTreeUri(appContext, uri)
            } else {
                DocumentFile.fromSingleUri(appContext, uri)
            }
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