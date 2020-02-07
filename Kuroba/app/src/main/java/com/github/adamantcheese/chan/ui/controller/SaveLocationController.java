/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.ui.controller;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.saver.FileWatcher;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.FilesAdapter;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.layout.FilesLayout;
import com.github.adamantcheese.chan.ui.layout.NewFolderLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class SaveLocationController
        extends Controller
        implements FileWatcher.FileWatcherCallback, FilesAdapter.Callback, FilesLayout.Callback, View.OnClickListener {
    private FilesLayout filesLayout;
    private FloatingActionButton setButton;
    private FloatingActionButton addButton;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private FileWatcher fileWatcher;
    private SaveLocationControllerMode mode;
    private SaveLocationControllerCallback callback;

    public SaveLocationController(
            Context context, SaveLocationControllerMode mode, SaveLocationControllerCallback callback
    ) {
        super(context);

        this.callback = callback;
        this.mode = mode;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.save_location_screen);

        view = inflate(context, R.layout.controller_save_location);
        filesLayout = view.findViewById(R.id.files_layout);
        filesLayout.setCallback(this);
        setButton = view.findViewById(R.id.set_button);
        setButton.setOnClickListener(this);
        addButton = view.findViewById(R.id.add_button);
        addButton.setOnClickListener(this);

        File saveLocation;

        if (mode == SaveLocationControllerMode.ImageSaveLocation) {
            if (ChanSettings.saveLocation.getFileApiBaseDir().get().isEmpty()) {
                throw new IllegalStateException("saveLocation is empty!");
            }

            saveLocation = new File(ChanSettings.saveLocation.getFileApiBaseDir().get());
        } else {
            if (ChanSettings.localThreadLocation.getFileApiBaseDir().get().isEmpty()) {
                throw new IllegalStateException("localThreadLocation is empty!");
            }

            saveLocation = new File(ChanSettings.localThreadLocation.getFileApiBaseDir().get());
        }

        fileWatcher = new FileWatcher(this, saveLocation);

        runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            initialize();
        } else {
            requestPermission();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == setButton) {
            onDirectoryChosen();
            navigationController.popController();
        } else if (v == addButton) {
            @SuppressLint("InflateParams")
            final NewFolderLayout dialogView = (NewFolderLayout) inflate(context, R.layout.layout_folder_add, null);

            new AlertDialog.Builder(context).setView(dialogView)
                    .setTitle(R.string.save_new_folder)
                    .setPositiveButton(R.string.add, (dialog, which) -> onPositionButtonClick(dialogView, dialog))
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                    .show();
        }
    }

    private void onPositionButtonClick(NewFolderLayout dialogView, DialogInterface dialog) {
        if (!dialogView.getFolderName().matches("\\A\\w+\\z")) {
            showToast(context, "Folder must be a word, no spaces");
        } else {
            File newDir = new File(
                    fileWatcher.getCurrentPath().getAbsolutePath() + File.separator + dialogView.getFolderName());

            if (!newDir.exists() && !newDir.mkdir()) {
                throw new IllegalStateException("Could not create directory " + newDir.getAbsolutePath());
            }

            fileWatcher.navigateTo(newDir);

            onDirectoryChosen();
            navigationController.popController();
        }

        dialog.dismiss();
    }

    private void onDirectoryChosen() {
        callback.onDirectorySelected(fileWatcher.getCurrentPath().getAbsolutePath());
    }

    @Override
    public void onFiles(FileWatcher.FileItems fileItems) {
        filesLayout.setFiles(fileItems);
    }

    @Override
    public void onBackClicked() {
        fileWatcher.navigateUp();
    }

    @Override
    public void onFileItemClicked(FileWatcher.FileItem fileItem) {
        if (fileItem.canNavigate()) {
            fileWatcher.navigateTo(fileItem.file);
        }
        // Else ignore, we only do folder selection here
    }

    private void requestPermission() {
        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                initialize();
            } else {
                runtimePermissionsHelper.showPermissionRequiredDialog(
                        context,
                        getString(R.string.save_location_storage_permission_required_title),
                        getString(R.string.save_location_storage_permission_required),
                        this::requestPermission
                );
            }
        });
    }

    private void initialize() {
        filesLayout.initialize();
        fileWatcher.initialize();
    }

    public interface SaveLocationControllerCallback {
        void onDirectorySelected(String dirPath);
    }

    public enum SaveLocationControllerMode {
        ImageSaveLocation,
        LocalThreadsSaveLocation
    }
}
