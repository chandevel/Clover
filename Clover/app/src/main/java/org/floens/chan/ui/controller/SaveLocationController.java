package org.floens.chan.ui.controller;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.FileItem;
import org.floens.chan.core.model.FileItems;
import org.floens.chan.core.saver.FileWatcher;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.adapter.FilesAdapter;
import org.floens.chan.ui.helper.RuntimePermissionsHelper;
import org.floens.chan.ui.layout.FilesLayout;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;

public class SaveLocationController extends Controller implements FileWatcher.FileWatcherCallback, FilesAdapter.Callback, FilesLayout.Callback, View.OnClickListener {
    private static final String TAG = "SaveLocationController";

    private FilesLayout filesLayout;
    private FloatingActionButton setButton;

    private RuntimePermissionsHelper runtimePermissionsHelper;
    private boolean gotPermission = false;

    private FileWatcher fileWatcher;
    private FileItems fileItems;

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
    public void onFiles(FileItems fileItems) {
        this.fileItems = fileItems;
        filesLayout.setFiles(fileItems);
    }

    @Override
    public void onBackClicked() {
        fileWatcher.navigateUp();
    }

    @Override
    public void onFileItemClicked(FileItem fileItem) {
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
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.write_permission_required_title)
                            .setMessage(R.string.write_permission_required)
                            .setCancelable(false)
                            .setNeutralButton(R.string.write_permission_app_settings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermission();
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.getPackageName()));
                                    AndroidUtils.openIntent(intent);
                                }
                            })
                            .setPositiveButton(R.string.write_permission_grant, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    requestPermission();
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void initialize() {
        filesLayout.initialize();
        fileWatcher.initialize();
    }
}
