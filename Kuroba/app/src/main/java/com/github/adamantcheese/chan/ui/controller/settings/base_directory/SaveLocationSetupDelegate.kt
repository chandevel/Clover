package com.github.adamantcheese.chan.ui.controller.settings.base_directory

import android.app.AlertDialog
import android.content.Context
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.ui.controller.SaveLocationController
import com.github.adamantcheese.chan.ui.controller.SaveLocationController.SaveLocationControllerCallback
import com.github.adamantcheese.chan.utils.BackgroundUtils
import java.io.File

class SaveLocationSetupDelegate(
        private val context: Context,
        private val callbacks: MediaControllerCallbacks,
        private val presenter: MediaSettingsControllerPresenter
) {

    fun getSaveLocation(): String {
        BackgroundUtils.ensureMainThread()

        if (ChanSettings.saveLocation.isSafDirActive()) {
            return ChanSettings.saveLocation.safBaseDir.get()
        }

        return ChanSettings.saveLocation.fileApiBaseDir.get()
    }

    fun showUseSAFOrOldAPIForSaveLocationDialog() {
        BackgroundUtils.ensureMainThread()

        callbacks.runWithWritePermissionsOrShowErrorToast(Runnable {
            val dialog = AlertDialog.Builder(context)
                    .setTitle(R.string.media_settings_use_saf_for_save_location_dialog_title)
                    .setMessage(R.string.media_settings_use_saf_for_save_location_dialog_message)
                    .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text) { _, _ ->
                        presenter.onSaveLocationUseSAFClicked()
                    }
                    .setNeutralButton(R.string.reset) { _, _ ->
                        presenter.resetSaveLocationBaseDir()

                        val defaultBaseDirFile = File(ChanSettings.saveLocation.fileApiBaseDir.get())
                        if (!defaultBaseDirFile.exists() && !defaultBaseDirFile.mkdirs()) {
                            callbacks.onCouldNotCreateDefaultBaseDir(defaultBaseDirFile.absolutePath)
                            return@setNeutralButton
                        }

                        callbacks.onFilesBaseDirectoryReset()
                    }
                    .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text) { _, _ ->
                        onSaveLocationUseOldApiClicked()
                    }
                    .create()

            dialog.show()
        })
    }

    /**
     * Select a directory where saved images will be stored via the old Java File API
     */
    private fun onSaveLocationUseOldApiClicked() {
        BackgroundUtils.ensureMainThread()

        val saveLocationController = SaveLocationController(context,
                SaveLocationController.SaveLocationControllerMode.ImageSaveLocation,
                SaveLocationControllerCallback { dirPath -> presenter.onSaveLocationChosen(dirPath) }
        )

        callbacks.pushController(saveLocationController)
    }


    interface MediaControllerCallbacks {
        fun runWithWritePermissionsOrShowErrorToast(func: Runnable)
        fun pushController(saveLocationController: SaveLocationController)
        fun setDescription(newLocation: String)
        fun updateSaveLocationViewText(newLocation: String)
        fun presentController(loadingViewController: LoadingViewController)
        fun onFilesBaseDirectoryReset()
        fun onCouldNotCreateDefaultBaseDir(path: String)
    }

}