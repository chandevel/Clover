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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.saver.FileWatcher;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.ui.adapter.FilesAdapter;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.layout.FilesLayout;
import com.github.adamantcheese.chan.ui.layout.NewFolderLayout;

import java.io.File;

public class SaveLocationController extends Controller implements FileWatcher.FileWatcherCallback, FilesAdapter.Callback, FilesLayout.Callback, View.OnClickListener {
    private FilesLayout filesLayout;
    private FloatingActionButton setButton;
    private FloatingActionButton addButton;

    private RuntimePermissionsHelper runtimePermissionsHelper;

    private FileWatcher fileWatcher;

    public SaveLocationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.save_location_screen);

        view = inflateRes(R.layout.controller_save_location);
        filesLayout = view.findViewById(R.id.files_layout);
        filesLayout.setCallback(this);
        setButton = view.findViewById(R.id.set_button);
        setButton.setOnClickListener(this);
        addButton = view.findViewById(R.id.add_button);
        addButton.setOnClickListener(this);

        File saveLocation = new File(ChanSettings.saveLocation.get());
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
            File currentPath = fileWatcher.getCurrentPath();
            ChanSettings.saveLocation.set(currentPath.getAbsolutePath());
            navigationController.popController();
        } else if (v == addButton) {
            @SuppressLint("InflateParams") final NewFolderLayout dialogView =
                    (NewFolderLayout) LayoutInflater.from(context)
                            .inflate(R.layout.layout_folder_add, null);

            new AlertDialog.Builder(context)
                    .setView(dialogView)
                    .setTitle(R.string.save_new_folder)
                    .setPositiveButton(R.string.add, (dialog, which) -> {
                        if (!dialogView.getFolderName().matches("\\A\\w+\\z")) {
                            Toast.makeText(context, "Folder must be a word, no spaces", Toast.LENGTH_SHORT).show();
                        } else {
                            File newDir = new File(fileWatcher.getCurrentPath().getAbsolutePath() + File.separator + dialogView.getFolderName());
                            //noinspection ResultOfMethodCallIgnored
                            newDir.mkdir();
                            fileWatcher.navigateTo(newDir);
                            ChanSettings.saveLocation.set(fileWatcher.getCurrentPath().getAbsolutePath());
                            navigationController.popController();
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                    .show();
        }
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
                        context.getString(R.string.save_location_storage_permission_required_title),
                        context.getString(R.string.save_location_storage_permission_required),
                        this::requestPermission
                );
            }
        });
    }

    private void initialize() {
        filesLayout.initialize();
        fileWatcher.initialize();
    }
}
