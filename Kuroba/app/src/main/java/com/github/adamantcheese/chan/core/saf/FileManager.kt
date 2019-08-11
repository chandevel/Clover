package com.github.adamantcheese.chan.core.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.github.adamantcheese.chan.core.saf.callback.DirectoryChooserCallback
import com.github.adamantcheese.chan.core.saf.callback.FileChooserCallback
import com.github.adamantcheese.chan.core.saf.callback.FileCreateCallback
import com.github.adamantcheese.chan.core.saf.callback.StartActivityCallbacks
import com.github.adamantcheese.chan.core.saf.file.AbstractFile
import com.github.adamantcheese.chan.core.saf.file.ExternalFile
import com.github.adamantcheese.chan.core.saf.file.RawFile
import com.github.adamantcheese.chan.utils.Logger
import java.io.File

class FileManager(
        private val appContext: Context
) {
    private val fileChooser = FileChooser(appContext)

    fun setCallbacks(startActivityCallbacks: StartActivityCallbacks) {
        fileChooser.setCallbacks(startActivityCallbacks)
    }

    fun removeCallbacks() {
        fileChooser.removeCallbacks()
    }

    //=======================================================
    // Api to open file/directory chooser and handling the result
    //=======================================================

    fun openChooseDirectoryDialog(callback: DirectoryChooserCallback): Boolean {
        return fileChooser.openChooseDirectoryDialog(callback)
    }

    fun openChooseFileDialog(callback: FileChooserCallback): Boolean {
        return fileChooser.openChooseFileDialog(callback)
    }

    fun openCreateFileDialog(callback: FileCreateCallback): Boolean {
        return openCreateFileDialog(null, callback)
    }

    fun openCreateFileDialog(filename: String?, callback: FileCreateCallback): Boolean {
        return fileChooser.openCreateFileDialog(filename, callback)
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return fileChooser.onActivityResult(requestCode, resultCode, data)
    }

    //=======================================================
    // Api to convert native file/documentFile classes into our own abstractions
    //=======================================================

    /**
     * Create a raw file from a path
     * */
    fun fromPath(path: String): RawFile {
        return fromRawFile(File(path))
    }

    /**
     * Create RawFile from Java File
     * */
    fun fromRawFile(file: File): RawFile {
        if (file.isFile) {
            return RawFile(AbstractFile.Root.FileRoot(file, file.name))
        }

        return RawFile(AbstractFile.Root.DirRoot(file))
    }

    /**
     * Create an external file from Uri
     * */
    fun fromUri(uri: Uri): ExternalFile? {
        val documentFile = toDocumentFile(uri)
        if (documentFile == null) {
            Logger.e(TAG, "fromUri() toDocumentFile() returned null")
            return null
        }

        return if (documentFile.isFile) {
            val filename = queryTreeName(uri)
            if (filename == null) {
                Logger.e(TAG, "fromUri() queryTreeName() returned null")
                return null
            }

            ExternalFile(appContext, AbstractFile.Root.FileRoot(uri, filename))
        } else {
            ExternalFile(appContext, AbstractFile.Root.DirRoot(uri))
        }
    }

    // TODO: may not work!
    private fun toDocumentFile(uri: Uri): DocumentFile? {
        if (!DocumentFile.isDocumentUri(appContext, uri)) {
            Logger.e(TAG, "Not a DocumentFile, uri = $uri")
            return null
        }

        val treeUri = try {
            DocumentFile.fromTreeUri(appContext, uri)
        } catch (e: IllegalArgumentException) {
            null
        }

        if (treeUri != null) {
            return treeUri
        }

        return try {
            DocumentFile.fromSingleUri(appContext, uri)
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "provided uri is neither a treeUri nor singleUri, uri = ${uri}")
            null
        }
    }

    // TODO: may not work!
    private fun queryTreeName(uri: Uri): String? {
        val contentResolver = appContext.contentResolver

        try {
            return contentResolver.query(uri, FILENAME_PROJECTION, null, null, null)?.use { cursor ->
                if (cursor.moveToNext()) {
                    return cursor.getString(0)
                }

                return null
            }
        } catch (e: Throwable) {
            Logger.e(TAG, "Error while trying to query for name, uri = $uri", e)
            return null
        }
    }

    companion object {
        private const val TAG = "FileManager"

        private val FILENAME_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    }
}