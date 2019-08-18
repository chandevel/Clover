package com.github.adamantcheese.chan.core.saf.file

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendManyEncoded
import com.github.adamantcheese.chan.core.getMimeFromFilename
import com.github.adamantcheese.chan.utils.Logger
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream

class ExternalFile(
        private val appContext: Context,
        private val root: Root<DocumentFile>,
        segments: MutableList<Segment> = mutableListOf()
) : AbstractFile(segments) {
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    override fun <T : AbstractFile> appendSubDirSegment(name: String): T {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        return super.appendSubDirSegmentInner(name)
    }

    override fun <T : AbstractFile> appendFileNameSegment(name: String): T {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        return super.appendFileNameSegmentInner(name)
    }

    override fun <T : AbstractFile> createNew(): T? {
        if (root is Root.FileRoot) {
            throw IllegalStateException("root is already FileRoot, cannot append anything anymore")
        }

        if (segments.isEmpty()) {
            // Root is probably already exists and there is no point in creating it again so just
            // return null here
            Logger.e(TAG, "No segments")
            return null
        }

        var newFile: DocumentFile? = null

        for (segment in segments) {
            val file = newFile ?: root.holder

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
                return ExternalFile(appContext, Root.FileRoot(newFile, segment.name)) as T
            }
        }

        if (newFile == null) {
            Logger.e(TAG, "result file is null")
            return null
        }

        if (segments.size < 1) {
            Logger.e(TAG, "Must be at least one segment!")
            return null
        }

        val lastSegment = segments.last()
        val isLastSegmentFilename = lastSegment.isFileName

        val root = if (isLastSegmentFilename) {
            Root.FileRoot(newFile, lastSegment.name)
        } else {
            Root.DirRoot(newFile)
        }

        return ExternalFile(appContext, root) as T
    }

    override fun <T : AbstractFile> clone(): T = ExternalFile(
            appContext,
            root.clone(),
            segments.toMutableList()) as T

    override fun exists(): Boolean {
        if (segments.isEmpty()) {
            return root.holder.exists()
        }

        return toDocumentFile()?.exists() ?: false
    }

    override fun isFile(): Boolean = toDocumentFile()?.isFile ?: false
    override fun isDirectory(): Boolean = toDocumentFile()?.isDirectory ?: false
    override fun canRead(): Boolean = toDocumentFile()?.canRead() ?: false
    override fun canWrite(): Boolean = toDocumentFile()?.canWrite() ?: false

    override fun <T : AbstractFile> getParent(): T? {
        if (segments.isNotEmpty()) {
            removeLastSegment()
            return this as T
        }

        val parent = when (root) {
            is Root.DirRoot -> root.holder.parentFile
            is Root.FileRoot -> root.holder.parentFile
        }

        if (parent == null) {
            Logger.e(TAG, "getParent() parentUri == null")
            return null
        }

        return ExternalFile(appContext, Root.DirRoot(parent)) as T
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

    override fun getName(): String {
        if (segments.isNotEmpty() && segments.last().isFileName) {
            return segments.last().name
        }

        val documentFile = toDocumentFile()
        if (documentFile == null) {
            throw IllegalStateException("getName() toDocumentFile() returned null")
        }

        return documentFile.name
                ?: throw IllegalStateException("Could not extract file name from document file")
    }

    override fun <T : AbstractFile> findFile(fileName: String): T? {
        if (root is Root.FileRoot) {
            throw IllegalStateException("Cannot use FileRoot as directory")
        }

        val filteredSegments = segments
                .map { it.name }

        var dirTree = root.holder

        for (segment in filteredSegments) {
            // FIXME: SLOW!!!
            for (documentFile in dirTree.listFiles()) {
                if (documentFile.name != null && documentFile.name == segment) {
                    dirTree = documentFile
                    break
                }
            }
        }


        // FIXME: SLOW!!!
        for (documentFile in dirTree.listFiles()) {
            if (documentFile.name != null && documentFile.name == fileName) {
                val root = if (documentFile.isFile) {
                    Root.FileRoot(documentFile, documentFile.name!!)
                } else {
                    Root.DirRoot(documentFile)
                }

                return ExternalFile(
                        appContext,
                        root) as T
            }
        }

        if (dirTree.name == fileName) {
            val root = if (dirTree.isFile) {
                Root.FileRoot(dirTree, dirTree.name!!)
            } else {
                Root.DirRoot(dirTree)
            }

            return ExternalFile(
                    appContext,
                    root) as T
        }

        // Not found
        return null
    }


    fun getParcelFileDescriptor(fileDescriptorMode: FileDescriptorMode): ParcelFileDescriptor? {
        return appContext.contentResolver.openFileDescriptor(
                root.holder.uri,
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
        if (segments.isEmpty()) {
            return root.holder
        }

        var documentFile: DocumentFile = root.holder
        var index = 0

        for (i in 0 until segments.size) {
            val segment = segments[i]

            val file = documentFile.listFiles()
                    .firstOrNull { file -> file.name == segment.name }

            if (file == null) {
                break
            }

            documentFile = file
            ++index
        }

        if (index != segments.size) {
            return createDocumentFileFromUri(documentFile.uri, index)
        }

        return documentFile
    }

    private fun createDocumentFileFromUri(uri: Uri, index: Int): DocumentFile? {
        val builder = uri.buildUpon()

        for (i in index until segments.size) {
            builder.appendEncodedPath(segments[i].name)
        }

        return DocumentFile.fromSingleUri(appContext, builder.build())
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