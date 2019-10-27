package com.github.adamantcheese.chan.ui.controller

import android.widget.Toast
import com.github.k1rakishou.fsaf.file.AbstractFile

interface MediaSettingsControllerCallbacks {
    fun showToast(message: String, length: Int = Toast.LENGTH_SHORT)
    fun updateLocalThreadsLocation(newLocation: String)

    fun askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    )

    fun askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    )

    fun updateLoadingViewText(text: String)
    fun updateSaveLocationViewText(newLocation: String)

    fun showCopyFilesDialog(
            filesCount: Int,
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    )

    fun onCopyDirectoryEnded(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile,
            result: Boolean
    )
}