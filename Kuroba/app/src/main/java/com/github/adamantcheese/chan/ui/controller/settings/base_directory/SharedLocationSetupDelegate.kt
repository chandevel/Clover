package com.github.adamantcheese.chan.ui.controller.settings.base_directory

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter
import com.github.adamantcheese.chan.ui.controller.LoadingViewController
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.AndroidUtils.showToast
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile

class SharedLocationSetupDelegate(
        private val context: Context,
        private val callbacks: SaveLocationSetupDelegate.MediaControllerCallbacks,
        private val presenter: MediaSettingsControllerPresenter,
        private val fileManager: FileManager
) : SharedLocationSetupDelegateCallbacks {
    private var loadingViewController: LoadingViewController? = null

    override fun updateLocalThreadsLocation(newLocation: String) {
        BackgroundUtils.ensureMainThread()
        callbacks.setDescription(newLocation)
    }

    override fun askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            oldBaseDirectory: AbstractFile?,
            newBaseDirectory: AbstractFile
    ) {
        BackgroundUtils.ensureMainThread()

        if (oldBaseDirectory == null) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        val isOldBaseDirExternal = oldBaseDirectory is ExternalFile
        val isNewBaseDirExternal = newBaseDirectory is ExternalFile

        if (isOldBaseDirExternal xor isNewBaseDirExternal) {
            // oldBaseDirectory and newBaseDirectory do not use the same provider (one of the uses a
            // RawFile and the other one uses ExternalFile). It's kinda hard to determine whether
            // they are the same directory or whether one is a parent of the other. So we are just
            // not gonna do in such case.
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        if (fileManager.areTheSame(oldBaseDirectory, newBaseDirectory)) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        if (fileManager.isChildOfDirectory(oldBaseDirectory, newBaseDirectory)) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        val moveThreadsDescription = getString(R.string.media_settings_move_threads_to_new_dir_description,
                oldBaseDirectory.getFullPath(),
                newBaseDirectory.getFullPath()
        )

        val alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.media_settings_move_threads_to_new_dir)
                .setMessage(moveThreadsDescription)
                .setPositiveButton(R.string.move) { _, _ ->
                    presenter.moveOldFilesToTheNewDirectory(oldBaseDirectory, newBaseDirectory)
                }
                .setNegativeButton(R.string.do_not) { dialog, _ -> dialog.dismiss() }
                .create()

        alertDialog.show()
    }

    override fun askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            oldBaseDirectory: AbstractFile?,
            newBaseDirectory: AbstractFile
    ) {
        BackgroundUtils.ensureMainThread()

        if (oldBaseDirectory == null) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        val isOldBaseDirExternal = oldBaseDirectory is ExternalFile
        val isNewBaseDirExternal = newBaseDirectory is ExternalFile

        if (isOldBaseDirExternal xor isNewBaseDirExternal) {
            // oldBaseDirectory and newBaseDirectory do not use the same provider (one of the uses a
            // RawFile and the other one uses ExternalFile). It's kinda hard to determine whether
            // they are the same directory or whether one is a parent of the other. So we are just
            // not gonna do in such case.
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        if (fileManager.areTheSame(oldBaseDirectory, newBaseDirectory)) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        if (fileManager.isChildOfDirectory(oldBaseDirectory, newBaseDirectory)) {
            showToast(context, R.string.done, Toast.LENGTH_LONG)
            return
        }

        val moveFilesDescription = getString(
                R.string.media_settings_move_saved_files_to_new_dir_description,
                oldBaseDirectory.getFullPath(),
                newBaseDirectory.getFullPath()
        )

        val alertDialog = AlertDialog.Builder(context)
                .setTitle(R.string.media_settings_move_saved_files_to_new_dir)
                .setMessage(moveFilesDescription)
                .setPositiveButton(R.string.move) { _, _ ->
                    presenter.moveOldFilesToTheNewDirectory(oldBaseDirectory, newBaseDirectory)
                }
                .setNegativeButton(R.string.do_not) { dialog, _ -> dialog.dismiss() }
                .create()

        alertDialog.show()
    }

    override fun updateLoadingViewText(text: String) {
        BackgroundUtils.ensureMainThread()
        loadingViewController?.updateWithText(text)
    }

    override fun updateSaveLocationViewText(newLocation: String) {
        BackgroundUtils.ensureMainThread()
        callbacks.updateSaveLocationViewText(newLocation)
    }

    override fun showCopyFilesDialog(
            filesCount: Int,
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile
    ) {
        BackgroundUtils.ensureMainThread()

        if (loadingViewController != null) {
            loadingViewController!!.stopPresenting()
            loadingViewController = null
        }

        val alertDialog = AlertDialog.Builder(context)
                .setTitle(getString(R.string.media_settings_copy_files))
                .setMessage(getString(R.string.media_settings_do_you_want_to_copy_files, filesCount))
                .setPositiveButton(R.string.media_settings_copy_files) { _, _ ->
                    loadingViewController = LoadingViewController(context, false).apply {
                        callbacks.presentController(this)
                    }

                    presenter.moveFilesInternal(oldBaseDirectory, newBaseDirectory)
                }
                .setNegativeButton(R.string.do_not) { dialog, _ -> dialog.dismiss() }
                .create()

        alertDialog.show()
    }

    override fun onCopyDirectoryEnded(
            oldBaseDirectory: AbstractFile,
            newBaseDirectory: AbstractFile,
            result: Boolean
    ) {
        BackgroundUtils.ensureMainThread()

        if (loadingViewController != null) {
            loadingViewController!!.stopPresenting()
            loadingViewController = null
        }

        if (!result) {
            showToast(context, R.string.media_settings_could_not_copy_files, Toast.LENGTH_LONG)
        } else {
            showDeleteOldFilesDialog(oldBaseDirectory)
            showToast(context, R.string.media_settings_files_copied, Toast.LENGTH_LONG)
        }
    }

    private fun showDeleteOldFilesDialog(oldBaseDirectory: AbstractFile) {
        val alertDialog = AlertDialog.Builder(context)
                .setTitle(getString(R.string.media_settings_would_you_like_to_delete_file_in_old_dir))
                .setMessage(getString(
                        R.string.media_settings_file_have_been_copied,
                        oldBaseDirectory.getFullPath()
                ))
                .setPositiveButton(R.string.delete) { _, _ -> onDeleteOldFilesClicked(oldBaseDirectory) }
                .setNegativeButton(R.string.do_not) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
        
        alertDialog.show()
    }
    
    private fun onDeleteOldFilesClicked(oldBaseDirectory: AbstractFile) {
        if (!fileManager.deleteContent(oldBaseDirectory)) {
            showToast(context, R.string.media_settings_could_not_delete_files_in_old_dir, Toast.LENGTH_LONG)
            return
        }
        
        showToast(context, R.string.media_settings_old_files_deleted, Toast.LENGTH_LONG)
    }

}