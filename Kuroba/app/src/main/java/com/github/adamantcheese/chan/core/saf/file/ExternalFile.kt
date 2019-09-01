package com.github.adamantcheese.chan.core.saf.file

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
import android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.appendManyEncoded
import com.github.adamantcheese.chan.core.getMimeFromFilename
import com.github.adamantcheese.chan.utils.Logger
import java.io.FileDescriptor
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ExternalFile(
        private val appContext: Context,
        private val root: Root<DocumentFile>,
        segments: MutableList<Segment> = mutableListOf()
) : AbstractFile(segments) {
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    override fun appendSubDirSegment(name: String): ExternalFile {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendSubDirSegmentInner(name) as ExternalFile
    }

    override fun appendFileNameSegment(name: String): ExternalFile {
        check(root !is Root.FileRoot) { "root is already FileRoot, cannot append anything anymore" }
        return super.appendFileNameSegmentInner(name) as ExternalFile
    }

    override fun createNew(): ExternalFile? {
        check(root !is Root.FileRoot) {
            "root is already FileRoot, cannot append anything anymore"
        }

        if (segments.isEmpty()) {
            // Root is probably already exists and there is no point in creating it again so just
            // return null here
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
                    Logger.e(TAG, "createNew() file.createDirectory() returned null, file.uri = ${file.uri}, " +
                            "segment.name = ${segment.name}")
                    return null
                }
            } else {
                newFile = file.createFile(mimeTypeMap.getMimeFromFilename(segment.name), segment.name)
                if (newFile == null) {
                    Logger.e(TAG, "createNew() file.createFile returned null, file.uri = ${file.uri}, " +
                            "segment.name = ${segment.name}")
                    return null
                }

                // Ignore any left segments (which we shouldn't have) after encountering fileName
                // segment
                return ExternalFile(appContext, Root.FileRoot(newFile, segment.name))
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

        return ExternalFile(appContext, root)
    }

    override fun clone(): ExternalFile = ExternalFile(
            appContext,
            root.clone(),
            segments.toMutableList())

    override fun exists(): Boolean = clone().toDocumentFile()?.exists() ?: false
    override fun isFile(): Boolean = clone().toDocumentFile()?.isFile ?: false
    override fun isDirectory(): Boolean = clone().toDocumentFile()?.isDirectory ?: false
    override fun canRead(): Boolean = clone().toDocumentFile()?.canRead() ?: false
    override fun canWrite(): Boolean = clone().toDocumentFile()?.canWrite() ?: false

    override fun getFullPath(): String {
        return Uri.parse(root.holder.uri.toString()).buildUpon()
                .appendManyEncoded(segments.map { segment -> segment.name })
                .build()
                .toString()
    }

    override fun delete(): Boolean {
        return clone().toDocumentFile()?.delete() ?: false
    }

    override fun getInputStream(): InputStream? {
        val contentResolver = appContext.contentResolver
        val documentFile = clone().toDocumentFile()

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
        val documentFile = clone().toDocumentFile()

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

        val documentFile = clone().toDocumentFile()
        if (documentFile == null) {
            throw IllegalStateException("getName() toDocumentFile() returned null")
        }

        return documentFile.name
                ?: throw IllegalStateException("Could not extract file name from document file")
    }

    override fun findFile(fileName: String): ExternalFile? {
        check(root !is Root.FileRoot) { "Cannot use FileRoot as directory" }

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
                        root)
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
                    root)
        }

        // Not found
        return null
    }

    override fun getLength(): Long = clone().toDocumentFile()?.length() ?: -1L

    override fun <T> withFileDescriptor(
            fileDescriptorMode: FileDescriptorMode,
            func: (FileDescriptor) -> T): Result<T> {
        return runCatching {
            getParcelFileDescriptor(fileDescriptorMode)?.use { pfd ->
                func(pfd.fileDescriptor)
            } ?: throw IllegalStateException("Could not get ParcelFileDescriptor " +
                    "from root with uri = ${root.holder.uri}")
        }
    }

    override fun listFiles(): List<ExternalFile> {
        check(root !is Root.FileRoot) { "Cannot use listFiles with FileRoot" }

        return clone()
                .toDocumentFile()
                ?.listFiles()
                ?.map { documentFile -> ExternalFile(appContext, Root.DirRoot(documentFile)) }
                ?: emptyList()
    }

    override fun lastModified(): Long {
        return clone().toDocumentFile()?.lastModified() ?: 0L
    }

    private fun getParcelFileDescriptor(fileDescriptorMode: FileDescriptorMode): ParcelFileDescriptor? {
        return appContext.contentResolver.openFileDescriptor(
                root.holder.uri,
                fileDescriptorMode.mode)
    }

    private fun toDocumentFile(): DocumentFile? {
        if (segments.isEmpty()) {
            return root.holder
        }

        var documentFile: DocumentFile = root.holder
        var index = 0

        for (i in 0 until segments.size) {
            val segment = segments[i]

            val file = fastFindFile(documentFile, segment)
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

    private fun fastFindFile(root: DocumentFile, segment: Segment): DocumentFile? {
        val name = 0
        val documentId = 1
        val selection = "$COLUMN_DISPLAY_NAME = ?"
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                root.uri,
                DocumentsContract.getDocumentId(root.uri))
        val projection = arrayOf(COLUMN_DISPLAY_NAME, COLUMN_DOCUMENT_ID)
        val contentResolver = appContext.contentResolver
        val lowerCaseFilename = segment.name.toLowerCase(Locale.US)

        return contentResolver.query(
                childrenUri,
                projection,
                selection,
                arrayOf(lowerCaseFilename),
                null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(name)) {
                    continue
                }

                val foundFileName = cursor.getString(name)
                        ?: continue

                if (!foundFileName.toLowerCase(Locale.US).startsWith(lowerCaseFilename)) {
                    continue
                }

                val uri = DocumentsContract.buildDocumentUriUsingTree(
                        root.uri,
                        cursor.getString(documentId))

                return@use DocumentFile.fromSingleUri(appContext, uri)
            }

            return@use null
        }
    }

    private fun createDocumentFileFromUri(uri: Uri, index: Int): DocumentFile? {
        val builder = uri.buildUpon()

        for (i in index until segments.size) {
            builder.appendPath(segments[i].name)
        }

        return DocumentFile.fromSingleUri(appContext, builder.build())
    }

    companion object {
        private const val TAG = "FileManager"
    }
}