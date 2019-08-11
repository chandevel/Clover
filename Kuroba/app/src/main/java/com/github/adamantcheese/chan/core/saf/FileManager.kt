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
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.utils.IOUtils
import com.github.adamantcheese.chan.utils.Logger
import java.io.File
import java.io.IOException

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
     * Create a raw file from a path.
     * Use this method to convert a file by this path into an AbstractFile.
     * */
    fun fromPath(path: String): RawFile {
        return fromRawFile(File(path))
    }

    /**
     * Create RawFile from Java File.
     * Use this method to convert this file into an AbstractFile.
     * */
    fun fromRawFile(file: File): RawFile {
        if (file.isFile) {
            return RawFile(AbstractFile.Root.FileRoot(file, file.name))
        }

        return RawFile(AbstractFile.Root.DirRoot(file))
    }

    /**
     * Create an external file from Uri.
     * Use this method to convert external file uri (file that may be located at sd card) into an
     * AbstractFile.
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

    /**
     * Use this method to create a new file that may be located at any user selected directory (it
     * may be stored at sd card or even in google drive, anywhere) or if user has not selected an
     * app directory via the SAF API it will be stored in the default external app directory
     * (like /storage/Kuroba)
     * */
    fun newFile(): AbstractFile {
        val uri = ChanSettings.saveLocationUri.get()
        if (uri.isNotEmpty()) {
            return ExternalFile(
                    appContext,
                    AbstractFile.Root.DirRoot(Uri.parse(uri)))
        }

        val path = ChanSettings.saveLocation.get()
        return RawFile(AbstractFile.Root.DirRoot(File(path)))
    }

    /**
     * AbstractFiles are mutable, so if you want to append some directory to another AbstractFile to
     * check whether it exists or not or something like this, you need to create a new AbstractFile
     * from the other AbstractFile (Just like with regular files, e.g. File(oldFile, "subDir").exists() )
     * */
    fun fromAbstractFile(file: AbstractFile): AbstractFile {
        return when (file) {
            is RawFile -> RawFile(file.getFullRoot())
            is ExternalFile -> ExternalFile(appContext, file.getFullRoot())
            else -> throw IllegalArgumentException("Not implemented for ${file.javaClass.name}")
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

    fun copyFile(source: AbstractFile, destination: AbstractFile): Boolean {
        try {
            return source.getInputStream()?.use { inputStream ->
                return@use destination.getOutputStream()?.use { outputStream ->
                    IOUtils.copy(inputStream, outputStream)
                    return@use true
                } ?: false
            } ?: false
        } catch (e: IOException) {
            Logger.e(TAG, "IOException while coping one file to another", e)
            return false
        }
    }

    companion object {
        private const val TAG = "FileManager"

        private val FILENAME_PROJECTION = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    }
}