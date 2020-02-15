package com.github.adamantcheese.chan.ui.controller.settings.base_directory

import android.app.AlertDialog
import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.database.DatabaseManager
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.controller.SaveLocationController
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.BackgroundUtils
import java.io.File

class ThreadsLocationSetupDelegate(
        private val context: Context,
        private val callbacks: MediaControllerCallbacks,
        private val presenter: MediaSettingsControllerPresenter,
        private val databaseManager: DatabaseManager,
        private val threadSaveManager: ThreadSaveManager
) {

    fun getLocalThreadsLocation(): String {
        BackgroundUtils.ensureMainThread()

        if (ChanSettings.localThreadLocation.isSafDirActive()) {
            return ChanSettings.localThreadLocation.safBaseDir.get()
        }

        return ChanSettings.localThreadLocation.fileApiBaseDir.get()
    }

    fun showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
        BackgroundUtils.ensureMainThread()

        callbacks.runWithWritePermissionsOrShowErrorToast(Runnable {
            val downloadingThreadsCount = databaseManager.runTask {
                databaseManager.databaseSavedThreadManager.countDownloadingThreads().call()
            }

            if (downloadingThreadsCount > 0) {
                showStopAllDownloadingThreadsDialog(downloadingThreadsCount)
                return@Runnable
            }

            val areThereActiveDownloads = threadSaveManager.isThereAtLeastOneActiveDownload
            if (areThereActiveDownloads) {
                showSomeDownloadsAreStillBeingProcessed()
                return@Runnable
            }

            val alertDialog = AlertDialog.Builder(context)
                    .setTitle(R.string.media_settings_use_saf_for_local_threads_location_dialog_title)
                    .setMessage(R.string.media_settings_use_saf_for_local_threads_location_dialog_message)
                    .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text) { _, _ ->
                        presenter.onLocalThreadsLocationUseSAFClicked()
                    }
                    .setNeutralButton(R.string.reset) { _, _ ->
                        presenter.resetLocalThreadsBaseDir()

                        val defaultBaseDirFile = File(ChanSettings.localThreadLocation.fileApiBaseDir.get())
                        if (!defaultBaseDirFile.exists() && !defaultBaseDirFile.mkdirs()) {
                            callbacks.onCouldNotCreateDefaultBaseDir(defaultBaseDirFile.absolutePath)
                            return@setNeutralButton
                        }

                        callbacks.onLocalThreadsBaseDirectoryReset()
                    }
                    .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text) { _, _ ->
                        onLocalThreadsLocationUseOldApiClicked()
                    }
                    .create()

            alertDialog.show()
        })
    }

    private fun showStopAllDownloadingThreadsDialog(downloadingThreadsCount: Long) {
        BackgroundUtils.ensureMainThread()

        val title = getString(R.string.media_settings_there_are_active_downloads, downloadingThreadsCount)
        val message = getString(R.string.media_settings_you_have_to_stop_all_downloads)

        AlertDialog.Builder(context).setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
                .create()
                .show()
    }

    private fun showSomeDownloadsAreStillBeingProcessed() {
        BackgroundUtils.ensureMainThread()

        val alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.media_settings_some_thread_downloads_are_still_processed)
                .setMessage(R.string.media_settings_do_not_terminate_the_app_manually)
                .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text) { _, _ ->
                    presenter.onLocalThreadsLocationUseSAFClicked()
                }
                .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text) { _, _ ->
                    onLocalThreadsLocationUseOldApiClicked()
                }
                .create()

        alertDialog.show()
    }

    /**
     * Select a directory where local threads will be stored via the old Java File API
     */
    private fun onLocalThreadsLocationUseOldApiClicked() {
        val mode = SaveLocationController.SaveLocationControllerMode.LocalThreadsSaveLocation

        val saveLocationController = SaveLocationController(context, mode) { dirPath ->
            presenter.onLocalThreadsLocationChosen(dirPath)
        }

        callbacks.pushController(saveLocationController)
    }

    interface MediaControllerCallbacks {
        fun runWithWritePermissionsOrShowErrorToast(func: Runnable)
        fun pushController(saveLocationController: SaveLocationController)
        fun onLocalThreadsBaseDirectoryReset()
        fun onCouldNotCreateDefaultBaseDir(path: String)
    }
}