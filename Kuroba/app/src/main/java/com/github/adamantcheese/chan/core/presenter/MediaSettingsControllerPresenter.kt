package com.github.adamantcheese.chan.core.presenter

import android.net.Uri
import android.widget.Toast
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.settings.base_directory.FilesBaseDirectory
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory
import com.github.adamantcheese.chan.utils.AndroidUtils.runOnUiThread
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileChooser
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.callback.DirectoryChooserCallback
import com.github.k1rakishou.fsaf.file.AbstractFile
import java.util.*
import java.util.concurrent.Executors

class MediaSettingsControllerPresenter(
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
                val oldLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                        LocalThreadsBaseDirectory::class.java
                )

                if (oldLocalThreadsDirectory == null) {
                    withCallbacks {
                        // TODO: string
                        showToast("Old local threads base directory is " +
                                "probably not registered (newBaseDirectoryFile returned null)")
                    }

                    return
                }

                ChanSettings.localThreadsLocationUri.set(uri.toString())
                val defaultDir = ChanSettings.getDefaultLocalThreadsLocation()

                ChanSettings.localThreadLocation.setNoUpdate(defaultDir)

                withCallbacks {
                    // TODO LocalThreadsLocation.setDescription()
                    updateLocalThreadsLocation(uri.toString())
                }

                val newLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                        LocalThreadsBaseDirectory::class.java
                )

                if (newLocalThreadsDirectory == null) {
                    withCallbacks {
                        // TODO: strings
                        showToast("New local threads base directory is probably not registered")
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
        val oldLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                LocalThreadsBaseDirectory::class.java
        )

        if (oldLocalThreadsDirectory == null) {
            withCallbacks {
                // TODO: String
                showToast("Old local threads base directory is " +
                        "probably not registered (newBaseDirectoryFile returned null)")
            }

            return
        }

        Logger.d(TAG, "SaveLocationController with LocalThreadsSaveLocation mode returned dir $dirPath")

        // Supa hack to get the callback called
        ChanSettings.localThreadLocation.setSync("")
        ChanSettings.localThreadLocation.setSync(dirPath)

        val newLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                LocalThreadsBaseDirectory::class.java
        )

        if (newLocalThreadsDirectory == null) {
            withCallbacks {
                // TODO: strings
                showToast("New local threads base directory is probably not registered")
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

    fun onSaveLocationChosen(dirPath: String) {
        val oldSaveFilesDirectory = fileManager.newBaseDirectoryFile(
                FilesBaseDirectory::class.java
        )

        if (oldSaveFilesDirectory == null) {
            withCallbacks {
                // TODO: String
                showToast("Old save files base directory is " +
                        "probably not registered (newBaseDirectoryFile returned null)")
            }

            return
        }

        Logger.d(TAG, "SaveLocationController with ImageSaveLocation mode returned dir $dirPath")

        // Supa hack to get the callback called
        ChanSettings.saveLocation.setSync("")
        ChanSettings.saveLocation.setSync(dirPath)

        val newSaveFilesDirectory = fileManager.newBaseDirectoryFile(
                FilesBaseDirectory::class.java
        )

        if (newSaveFilesDirectory == null) {
            withCallbacks {
                // TODO: strings
                showToast("New save files base directory is probably not registered")
            }

            return
        }

        withCallbacks {
            askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                    oldSaveFilesDirectory,
                    newSaveFilesDirectory
            )
        }
    }

    /**
     * Select a directory where saved images will be stored via the SAF
     */
    fun onSaveLocationUseSAFClicked() {
        fileChooser.openChooseDirectoryDialog(object : DirectoryChooserCallback() {
            override fun onResult(uri: Uri) {
                ChanSettings.saveLocationUri.set(uri.toString())

                val defaultDir = ChanSettings.getDefaultSaveLocationDir()
                ChanSettings.saveLocation.setNoUpdate(defaultDir)

                withCallbacks {
                    updateSaveLocationViewText(uri.toString())
                }
            }

            override fun onCancel(reason: String) {
                withCallbacks {
                    showToast(reason, Toast.LENGTH_LONG)
                }
            }
        })
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

        val filesCount = fileManager.listFiles(oldBaseDirectory).size
        if (filesCount == 0) {
            withCallbacks {
                // TODO: strings
                showToast("No files to copy")
            }

            return
        }

        withCallbacks {
            showCopyFilesDialog(oldBaseDirectory, newBaseDirectory)
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
                    false
            ) { fileIndex, totalFilesCount ->
                if (callbacks == null) {
                    // User left the MediaSettings screen, we need to cancel the file copying
                    return@copyDirectoryWithContent true
                }

                withCallbacks {
                    // TODO: strings
                    val text = String.format(
                            Locale.US,
                            // TODO: strings
                            "Copying file %d out of %d",
                            fileIndex,
                            totalFilesCount)

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

    interface MediaSettingsControllerCallbacks {
        fun showToast(message: String, length: Int = Toast.LENGTH_SHORT)
        fun updateLocalThreadsLocation(newLocation: String)

        fun askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                oldBaseDirectory: AbstractFile,
                newBaseDirectory: AbstractFile
        )

        fun updateLoadingViewText(newLocation: String)
        fun updateSaveLocationViewText(newLocation: String)

        fun showCopyFilesDialog(
                oldBaseDirectory: AbstractFile,
                newBaseDirectory: AbstractFile
        )

        fun onCopyDirectoryEnded(
                oldBaseDirectory: AbstractFile,
                newBaseDirectory: AbstractFile,
                result: Boolean
        )
    }

    companion object {
        private const val TAG = "MediaSettingsControllerPresenter"
    }
}