package com.github.adamantcheese.chan.core.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.github.adamantcheese.chan.core.getMimeFromFilename
import com.github.adamantcheese.chan.core.saf.callback.*
import com.github.adamantcheese.chan.utils.Logger
import java.lang.Exception
import java.lang.IllegalArgumentException

internal class FileChooser(
        private val appContext: Context
) {
    private val callbacksMap = hashMapOf<Int, ChooserCallback>()
    private val mimeTypeMap = MimeTypeMap.getSingleton()

    private var requestCode = 10000
    private var startActivityCallbacks: StartActivityCallbacks? = null

    internal fun setCallbacks(startActivityCallbacks: StartActivityCallbacks) {
        this.startActivityCallbacks = startActivityCallbacks
    }

    internal fun removeCallbacks() {
        this.startActivityCallbacks = null
    }

    internal fun openChooseDirectoryDialog(directoryChooserCallback: DirectoryChooserCallback) {
        startActivityCallbacks?.let { callbacks ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.putExtra("android.content.extra.SHOW_ADVANCED", true)

            intent.addFlags(
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val nextRequestCode = ++requestCode
            callbacksMap[nextRequestCode] = directoryChooserCallback as ChooserCallback

            try {
                callbacks.myStartActivityForResult(intent, nextRequestCode)
            } catch (e: Exception) {
                callbacksMap.remove(nextRequestCode)
                directoryChooserCallback.onCancel(e.message
                        ?: "openChooseDirectoryDialog() Unknown error")
            }
        }
    }

    internal fun openChooseFileDialog(fileChooserCallback: FileChooserCallback) {
        startActivityCallbacks?.let { callbacks ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"

            val nextRequestCode = ++requestCode
            callbacksMap[nextRequestCode] = fileChooserCallback as ChooserCallback

            try {
                callbacks.myStartActivityForResult(intent, nextRequestCode)
            } catch (e: Exception) {
                callbacksMap.remove(nextRequestCode)
                fileChooserCallback.onCancel(e.message ?: "openChooseFileDialog() Unknown error")
            }
        }
    }

    internal fun openCreateFileDialog(
            fileName: String,
            fileCreateCallback: FileCreateCallback
    ) {
        startActivityCallbacks?.let { callbacks ->
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addFlags(
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            intent.addCategory(Intent.CATEGORY_OPENABLE)

            if (fileName != null) {
                intent.type = mimeTypeMap.getMimeFromFilename(fileName)
                intent.putExtra(Intent.EXTRA_TITLE, fileName)
            }

            val nextRequestCode = ++requestCode
            callbacksMap[nextRequestCode] = fileCreateCallback as ChooserCallback

            try {
                callbacks.myStartActivityForResult(intent, nextRequestCode)
            } catch (e: Exception) {
                callbacksMap.remove(nextRequestCode)
                fileCreateCallback.onCancel(e.message ?: "openCreateFileDialog() Unknown error")
            }
        }
    }

    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val callback = callbacksMap[requestCode]
        if (callback == null) {
            Logger.d(TAG, "Callback is already removed from the map, resultCode = $requestCode")
            return false
        }

        try {
            if (startActivityCallbacks == null) {
                // Skip all requests when the callback is not set
                Logger.d(TAG, "Callback is not attached")
                return false
            }

            when (callback) {
                is DirectoryChooserCallback -> {
                    handleDirectoryChooserCallback(callback, resultCode, data)
                }
                is FileChooserCallback -> {
                    handleFileChooserCallback(callback, resultCode, data)
                }
                is FileCreateCallback -> {
                    handleFileCreateCallback(callback, resultCode, data)
                }
                else -> throw IllegalArgumentException("Not implemented for ${callback.javaClass.name}")
            }

            return true
        } finally {
            callbacksMap.remove(requestCode)
        }
    }

    private fun handleFileCreateCallback(
            callback: FileCreateCallback,
            resultCode: Int,
            intent: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            val msg = "handleFileCreateCallback() Non OK result ($resultCode)"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (intent == null) {
            val msg = "handleFileCreateCallback() Intent is null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val read = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val write = (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0

        if (!read) {
            val msg = "handleFileCreateCallback() No grant read uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (!write) {
            val msg = "handleFileCreateCallback() No grant write uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val uri = intent.data
        if (uri == null) {
            val msg = "handleFileCreateCallback() intent.getData() == null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        callback.onResult(uri)
    }

    private fun handleFileChooserCallback(
            callback: FileChooserCallback,
            resultCode: Int,
            intent: Intent?
    ) {
        if (resultCode != Activity.RESULT_OK) {
            val msg = "handleFileChooserCallback() Non OK result ($resultCode)"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (intent == null) {
            val msg = "handleFileChooserCallback() Intent is null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val read = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val write = (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0

        if (!read) {
            val msg = "handleFileChooserCallback() No grant read uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (!write) {
            val msg = "handleFileChooserCallback() No grant write uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val uri = intent.data
        if (uri == null) {
            val msg = "handleFileChooserCallback() intent.getData() == null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        callback.onResult(uri)
    }

    private fun handleDirectoryChooserCallback(
            callback: DirectoryChooserCallback,
            resultCode: Int,
            intent: Intent?
    ) {
        if (resultCode != Activity.RESULT_OK) {
            val msg = "handleDirectoryChooserCallback() Non OK result ($resultCode)"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (intent == null) {
            val msg = "handleDirectoryChooserCallback() Intent is null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val read = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val write = (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0

        if (!read) {
            val msg = "handleDirectoryChooserCallback() No grant read uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (!write) {
            val msg = "handleDirectoryChooserCallback() No grant write uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val uri = intent.data
        if (uri == null) {
            val msg = "handleDirectoryChooserCallback() intent.getData() == null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val contentResolver = appContext.contentResolver
        contentResolver.takePersistableUriPermission(uri, flags)

        callback.onResult(treeDocumentUri)
    }

    companion object {
        private const val TAG = "FileChooser"
    }
}