/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.controller;

import android.Manifest;
import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.saver.FileWatcher;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.adapter.FilesAdapter;
import org.floens.chan.ui.helper.RuntimePermissionsHelper;
import org.floens.chan.ui.layout.FilesLayout;

import java.io.File;

import static org.floens.chan.R.string.save_location_storage_permission_required;
import static org.floens.chan.R.string.save_location_storage_permission_required_title;

public class SaveLocationController extends Controller implements FileWatcher.FileWatcherCallback, FilesAdapter.Callback, FilesLayout.Callback, View.OnClickListener {
    private static final String TAG = "SaveLocationController";

    private FilesLayout filesLayout;
    private FloatingActionButton setButton;

    private RuntimePermissionsHelper runtimePermissionsHelper;
    private boolean gotPermission = false;

    private FileWatcher fileWatcher;
    private FileWatcher.FileItems fileItems;

    public SaveLocationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.setTitle(R.string.save_location_screen);

        view = inflateRes(R.layout.controller_save_location);
        filesLayout = (FilesLayout) view.findViewById(R.id.files_layout);
        filesLayout.setCallback(this);
        setButton = (FloatingActionButton) view.findViewById(R.id.set_button);
        setButton.setOnClickListener(this);

        File saveLocation = new File(ChanSettings.saveLocation.get());
        fileWatcher = new FileWatcher(this, saveLocation);

        runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        gotPermission = hasPermission();
        if (gotPermission) {
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
        }
    }

    @Override
    public void onFiles(FileWatcher.FileItems fileItems) {
        this.fileItems = fileItems;
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

    private boolean hasPermission() {
        return runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission() {
        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, new RuntimePermissionsHelper.Callback() {
            @Override
            public void onRuntimePermissionResult(boolean granted) {
                gotPermission = granted;
                if (gotPermission) {
                    initialize();
                } else {
                    runtimePermissionsHelper.showPermissionRequiredDialog(
                            context,
                            context.getString(save_location_storage_permission_required_title),
                            context.getString(save_location_storage_permission_required),
                            new RuntimePermissionsHelper.PermissionRequiredDialogCallback() {
                                @Override
                                public void retryPermissionRequest() {
                                    requestPermission();
                                }
                            }
                    );
                }
            }
        });
    }

    private void initialize() {
        filesLayout.initialize();
        fileWatcher.initialize();
    }
}
