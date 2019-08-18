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
import java.lang.IllegalStateException

class FileManager(
        private val appContext: Context
) {
    private val fileChooser = FileChooser(appContext)

    /**
     * Used for calling Android File picker
     * */
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
     * Use this method to convert a java File by this path into an AbstractFile.
     * */
    fun fromPath(path: String): RawFile {
        return fromRawFile(File(path))
    }

    /**
     * Create RawFile from Java File.
     * Use this method to convert a java File into an AbstractFile.
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
    fun fromUri(uri: Uri): ExternalFile {
        val documentFile = toDocumentFile(uri)
        if (documentFile == null) {
            throw IllegalStateException("fromUri() toDocumentFile() returned null")
        }

        return if (documentFile.isFile) {
            val filename = documentFile.name
            if (filename == null) {
                throw IllegalStateException("fromUri() queryTreeName() returned null")
            }

            ExternalFile(appContext, AbstractFile.Root.FileRoot(documentFile, filename))
        } else {
            ExternalFile(appContext, AbstractFile.Root.DirRoot(documentFile))
        }
    }

    /**
     * Instantiates a new AbstractFile with the root being in the app's base directory (the Kuroba
     * directory in case of using raw file api and the user's selected directory in case of using SAF).
     * */
    fun newFile(): AbstractFile {
        val uri = ChanSettings.saveLocationUri.get()
        if (uri.isNotEmpty()) {
            val rootDirectory = DocumentFile.fromTreeUri(appContext, Uri.parse(uri))
            if (rootDirectory == null) {
                throw IllegalStateException("Root directory cannot be null!")
            }

            return ExternalFile(
                    appContext,
                    AbstractFile.Root.DirRoot(rootDirectory))
        }

        val path = ChanSettings.saveLocation.get()
        return RawFile(AbstractFile.Root.DirRoot(File(path)))
    }

    private fun toDocumentFile(uri: Uri): DocumentFile? {
        if (!DocumentFile.isDocumentUri(appContext, uri)) {
            Logger.e(TAG, "Not a DocumentFile, uri = $uri")
            return null
        }

        val treeUri = try {
            // Will throw an exception if uri is not a treeUri. Hacky as fuck but I don't know
            // another way to check it.
            DocumentFile.fromTreeUri(appContext, uri)
        } catch (ignored: IllegalArgumentException) {
            Logger.d(TAG, "Uri is not a treeUri, uri = $uri")
            null
        }

        if (treeUri != null) {
            return treeUri
        }

        return try {
            DocumentFile.fromSingleUri(appContext, uri)
        } catch (e: IllegalArgumentException) {
            Logger.e(TAG, "Provided uri is neither a treeUri nor singleUri, uri = $uri")
            null
        }
    }

    /**
     * Copy one file's contents into another
     * */
    fun copyFileContents(source: AbstractFile, destination: AbstractFile): Boolean {
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
    }
}