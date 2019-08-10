package com.github.adamantcheese.chan.core.saf

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import com.github.adamantcheese.chan.core.saf.callback.ChooserCallback
import com.github.adamantcheese.chan.core.saf.callback.DirectoryChooserCallback
import com.github.adamantcheese.chan.core.saf.callback.FileChooserCallback
import com.github.adamantcheese.chan.core.saf.callback.StartActivityCallbacks
import com.github.adamantcheese.chan.utils.Logger

internal class FileChooser(
        private val appContext: Context
) {
    private val callbacksMap = hashMapOf<Int, ChooserCallback>()

    private var requestCode = 10000
    private var startActivityCallbacks: StartActivityCallbacks? = null

    internal fun setCallbacks(startActivityCallbacks: StartActivityCallbacks) {
        this.startActivityCallbacks = startActivityCallbacks
    }

    internal fun removeCallbacks() {
        this.startActivityCallbacks = null
    }

    internal fun openChooseDirectoryDialog(directoryChooserCallback: DirectoryChooserCallback): Boolean {
        return startActivityCallbacks?.let { callbacks ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )

            val nextRequestCode = ++requestCode
            callbacksMap[nextRequestCode] = directoryChooserCallback as ChooserCallback

            return@let callbacks.myStartActivityForResult(intent, nextRequestCode)
        } ?: false
    }

    internal fun openChooseFileDialog(fileChooserCallback: FileChooserCallback): Boolean {
        return startActivityCallbacks?.let { callbacks ->
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            intent.addCategory(Intent.CATEGORY_OPENABLE)

            val nextRequestCode = ++requestCode
            callbacksMap[nextRequestCode] = fileChooserCallback as ChooserCallback

            return@let callbacks.myStartActivityForResult(intent, nextRequestCode)
        } ?: false
    }

    internal fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (!callbacksMap.containsKey(requestCode)) {
            return false
        }

        try {
            val callback = callbacksMap[requestCode]
            if (callback == null) {
                Logger.d(TAG, "Callback is already removed from the map")
                return false
            }

            if (startActivityCallbacks == null) {
                // Skip all requests when the callback is not set
                return false
            }

            when (callback) {
                is DirectoryChooserCallback -> {
                    handleDirectoryChooserCallback(callback, resultCode, data)
                }
                is FileChooserCallback -> {
                    handleFileChooserCallback(callback, resultCode, data)
                }
            }

            return true
        } finally {
            callbacksMap.remove(requestCode)
        }
    }

    private fun handleFileChooserCallback(
            callback: FileChooserCallback,
            resultCode: Int,
            intent: Intent?
    ) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun handleDirectoryChooserCallback(
            callback: DirectoryChooserCallback,
            resultCode: Int,
            intent: Intent?
    ) {
        if (resultCode != Activity.RESULT_OK) {
            val msg = "Non OK result ($resultCode)"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (intent == null) {
            val msg = "Intent is null"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val read = (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        val write = (intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0

        if (!read) {
            val msg = "No grant read uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        if (!write) {
            val msg = "No grant write uri permission given"

            Logger.e(TAG, msg)
            callback.onCancel(msg)
            return
        }

        val uri = intent.data
        if (uri == null) {
            val msg = "intent.getData() == null"

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