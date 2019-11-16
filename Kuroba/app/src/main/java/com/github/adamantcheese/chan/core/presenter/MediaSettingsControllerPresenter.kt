package com.github.adamantcheese.chan.core.presenter

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.controller.MediaSettingsControllerCallbacks
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.ui.settings.base_directory.SavedFilesBaseDirectory
import com.github.adamantcheese.chan.utils.AndroidUtils.runOnUiThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.TraverseMode
import com.github.k1rakishou.fsaf.callback.DirectoryChooserCallback
import com.github.k1rakishou.fsaf.file.AbstractFile
import java.util.concurrent.Executors

class MediaSettingsControllerPresenter(
        private val appContext: Context,
        private val fileManager: FileManager,
        private val fileChooser: FileChooser,
        private var callbacks: MediaSettingsControllerCallbacks?
) {
    private val fileCopyingExecutor = Executors.newSingleThreadExecutor()

    fun onDestroy() {
        callbacks = null
        fileCopyingExecutor.shutdown()
    }

    /**
     * Select a directory where local threads will be stored via the SAF
     */
    fun onLocalThreadsLocationUseSAFClicked() {
        fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
            override fun onResult(uri: Uri) {
                val oldLocalThreadsDirectory =
                        fileManager.newBaseDirectoryFile<LocalThreadsBaseDirectory>()

                if (oldLocalThreadsDirectory == null) {
                    val toastStringId = R.string.media_settings_old_threads_base_dir_not_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                if (fileManager.isBaseDirAlreadyRegistered<LocalThreadsBaseDirectory>(uri)) {
                    val toastStringId = R.string.media_settings_base_directory_is_already_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                Logger.d(TAG, "onLocalThreadsLocationUseSAFClicked dir = $uri")
                ChanSettings.localThreadLocation.setSafBaseDir(uri)
                ChanSettings.localThreadLocation.resetFileDir()

                withCallbacks {
                    updateLocalThreadsLocation(uri.toString())
                }

                val newLocalThreadsDirectory =
                        fileManager.newBaseDirectoryFile<LocalThreadsBaseDirectory>()

                if (newLocalThreadsDirectory == null) {
                    val toastStringId = R.string.media_settings_new_threads_base_dir_not_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                withCallbacks {
                    askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                            oldLocalThreadsDirectory,
                            newLocalThreadsDirectory
                    )
                }
            }

            override fun onCancel(reason: String) {
                withCallbacks {
                    showToast(reason, Toast.LENGTH_LONG)
                }
            }
        })
    }

    fun onLocalThreadsLocationChosen(dirPath: String) {
        val oldLocalThreadsDirectory =
                fileManager.newBaseDirectoryFile<LocalThreadsBaseDirectory>()

        if (oldLocalThreadsDirectory == null) {
            val toastStringId = R.string.media_settings_old_threads_base_dir_not_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        if (fileManager.isBaseDirAlreadyRegistered<LocalThreadsBaseDirectory>(dirPath)) {
            val toastStringId = R.string.media_settings_base_directory_is_already_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        Logger.d(TAG, "onLocalThreadsLocationChosen dir = $dirPath")
        ChanSettings.localThreadLocation.setFileBaseDir(dirPath)

        val newLocalThreadsDirectory =
                fileManager.newBaseDirectoryFile<LocalThreadsBaseDirectory>()

        if (newLocalThreadsDirectory == null) {
            val toastStringId = R.string.media_settings_new_threads_base_dir_not_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        withCallbacks {
            askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                    oldLocalThreadsDirectory,
                    newLocalThreadsDirectory
            )
        }
    }

    /**
     * Select a directory where saved images will be stored via the SAF
     */
    fun onSaveLocationUseSAFClicked() {
        fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
            override fun onResult(uri: Uri) {
                val oldSavedFileBaseDirectory =
                        fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

                if (oldSavedFileBaseDirectory == null) {
                    val toastStringId =
                            R.string.media_settings_old_saved_files_base_dir_not_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                if (fileManager.isBaseDirAlreadyRegistered<SavedFilesBaseDirectory>(uri)) {
                    val toastStringId = R.string.media_settings_base_directory_is_already_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                Logger.d(TAG, "onSaveLocationUseSAFClicked dir = $uri")
                ChanSettings.saveLocation.setSafBaseDir(uri)
                ChanSettings.saveLocation.resetFileDir()

                withCallbacks {
                    updateSaveLocationViewText(uri.toString())
                }

                val newSavedFilesBaseDirectory =
                        fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

                if (newSavedFilesBaseDirectory == null) {
                    val toastStringId =
                            R.string.media_settings_new_saved_files_base_dir_not_registered

                    withCallbacks {
                        showToast(appContext.getString(toastStringId))
                    }

                    return
                }

                withCallbacks {
                    askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
                            oldSavedFileBaseDirectory,
                            newSavedFilesBaseDirectory
                    )
                }
            }

            override fun onCancel(reason: String) {
                withCallbacks {
                    showToast(reason, Toast.LENGTH_LONG)
                }
            }
        })
    }

    fun onSaveLocationChosen(dirPath: String) {
        val oldSaveFilesDirectory = fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()

        if (oldSaveFilesDirectory == null) {
            val toastStringId = R.string.media_settings_old_saved_files_base_dir_not_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        if (fileManager.isBaseDirAlreadyRegistered<SavedFilesBaseDirectory>(dirPath)) {
            val toastStringId = R.string.media_settings_base_directory_is_already_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        Logger.d(TAG, "onSaveLocationChosen dir = $dirPath")
        ChanSettings.saveLocation.setFileBaseDir(dirPath)

        val newSaveFilesDirectory = fileManager.newBaseDirectoryFile<SavedFilesBaseDirectory>()
        if (newSaveFilesDirectory == null) {
            val toastStringId = R.string.media_settings_new_saved_files_base_dir_not_registered

            withCallbacks {
                showToast(appContext.getString(toastStringId))
            }

            return
        }

        withCallbacks {
            askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
                    oldSaveFilesDirectory,
                    newSaveFilesDirectory
            )
        }
    }

    fun moveOldFilesToTheNewDirectory(
            oldBaseDirectory: AbstractFile?,
            newBaseDirectory: AbstractFile?
    ) {
        if (oldBaseDirectory == null || newBaseDirectory == null) {
            Logger.e(TAG, "One of the directories is null, cannot copy " +
                    "(oldBaseDirectory is null == " + (oldBaseDirectory == null) + ")" +
                    ", newLocalThreadsDirectory is null == " + (newBaseDirectory == null) + ")")
            return
        }

        Logger.d(TAG,
                "oldLocalThreadsDirectory = " + oldBaseDirectory.getFullPath()
                        + ", newLocalThreadsDirectory = " + newBaseDirectory.getFullPath()
        )

        var filesCount = 0

        fileManager.traverseDirectory(
                oldBaseDirectory,
                true,
                TraverseMode.OnlyFiles
        ) {
            ++filesCount
        }

        if (filesCount == 0) {
            withCallbacks {
                showToast(appContext.getString(R.string.media_settings_no_files_to_copy))
            }

            return
        }

        withCallbacks {
            showCopyFilesDialog(filesCount, oldBaseDirectory, newBaseDirectory)
        }
    }

    fun moveFilesInternal(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    ) {
        fileCopyingExecutor.execute {
            val result = fileManager.copyDirectoryWithContent(
                    oldBaseDirectory,
                    newBaseDirectory,
                    true
            ) { fileIndex, totalFilesCount ->
                if (callbacks == null) {
                    // User left the MediaSettings screen, we need to cancel the file copying
                    return@copyDirectoryWithContent true
                }

                withCallbacks {
                    val text = appContext.getString(
                            R.string.media_settings_copying_file,
                            fileIndex,
                            totalFilesCount
                    )

                    updateLoadingViewText(text)
                }

                return@copyDirectoryWithContent false
            }

            withCallbacks {
                onCopyDirectoryEnded(
                        oldBaseDirectory,
                        newBaseDirectory,
                        result
                )
            }
        }
    }

    private fun withCallbacks(func: MediaSettingsControllerCallbacks.() -> Unit) {
        callbacks?.let {
            runOnUiThread {
                func(it)
            }
        }
    }

    companion object {
        private const val TAG = "MediaSettingsControllerPresenter"
    }
}