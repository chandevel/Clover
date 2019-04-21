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
package org.floens.chan.ui.controller.export;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.presenter.ImportExportSettingsPresenter;
import org.floens.chan.core.repository.ImportExportRepository;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.helper.RuntimePermissionsHelper;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;
import java.util.Locale;

public class ImportExportSettingsController extends SettingsController implements
        ImportExportSettingsPresenter.ImportExportSettingsCallbacks {

    @Nullable
    private ImportExportSettingsPresenter presenter;

    @Nullable
    private OnExportSuccessCallbacks callbacks;

    private LoadingViewController loadingViewController;
    private File settingsFile = new File(ChanSettings.saveLocation.get(), ImportExportRepository.EXPORT_FILE_NAME);

    public ImportExportSettingsController(Context context, @NonNull OnExportSuccessCallbacks callbacks) {
        super(context);

        this.callbacks = callbacks;
        this.loadingViewController = new LoadingViewController(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_import_export);

        presenter = new ImportExportSettingsPresenter(this);

        setupLayout();
        populatePreferences();
        buildPreferences();

        showCreateCloverDirectoryDialog();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (presenter != null) {
            presenter.onDestroy();
        }

        callbacks = null;
    }

    protected void setupLayout() {
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    private void populatePreferences() {
        // Import/export settings group
        {
            SettingsGroup group = new SettingsGroup(context.getString(R.string.import_or_export_settings));

            group.add(new LinkSettingView(this,
                    context.getString(R.string.export_settings),
                    context.getString(R.string.export_settings_to_a_file),
                    v -> onExportClicked()));

            group.add(new LinkSettingView(this,
                    context.getString(R.string.import_settings),
                    context.getString(R.string.import_settings_from_a_file),
                    v -> onImportClicked()));

            groups.add(group);
        }
    }

    private void onExportClicked() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.error_external_storage_is_not_mounted));
            return;
        }

        getPermissionHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (granted) {
                navigationController.presentController(loadingViewController);

                if (presenter != null) {
                    presenter.doExport(settingsFile);
                }
            } else {
                getPermissionHelper().showPermissionRequiredDialog(context,
                        context.getString(R.string.update_storage_permission_required_title),
                        context.getString(R.string.storage_permission_required_to_export_settings),
                        this::onExportClicked);
            }
        });
    }

    private void showCreateCloverDirectoryDialog() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.error_external_storage_is_not_mounted));
            return;
        }

        // if we already have the permission and the Clover directory already exists - do not show
        // the dialog again
        if (getPermissionHelper().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (settingsFile.getParentFile().exists()) {
                return;
            }
        }

        if (!AndroidUtils.isApi16()) {
            // we can't request READ_EXTERNAL_STORAGE permission on devices with API level below 16
            onPermissionGrantedForDirectoryCreation();
            return;
        }

        // Ask the user's permission to check whether the Clover directory exists and create it if it doesn't
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.clover_directory_may_not_exist_title))
                .setMessage(context.getString(R.string.clover_directory_may_not_exist_message))
                .setPositiveButton(context.getString(R.string.create), (dialog1, which) -> getPermissionHelper().requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, granted -> {
                    if (granted) {
                        onPermissionGrantedForDirectoryCreation();
                    }
                }))
                .setNegativeButton(context.getString(R.string.do_not), null)
                .create();

        dialog.show();
    }

    private void onPermissionGrantedForDirectoryCreation() {
        if (settingsFile.getParentFile().exists()) {
            showMessage(context.getString(R.string.default_clover_directory_already_exists));
            return;
        }

        if (!createExportDirectoryIfNotExist()) {
            showMessage(context.getString(R.string.could_not_create_dir_for_export_error_text));
        } else {
            showMessage(context.getString(R.string.default_clover_directory_created));
        }
    }

    private boolean createExportDirectoryIfNotExist() {
        if (!settingsFile.getParentFile().exists()) {
            return settingsFile.getParentFile().mkdirs();
        }

        return true;
    }

    private void onImportClicked() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.error_external_storage_is_not_mounted));
            return;
        }

        if (!AndroidUtils.isApi16()) {
            // we can't request READ_EXTERNAL_STORAGE permission on devices with API level below 16
            onPermissionGrantedForImport();
            return;
        }

        getPermissionHelper().requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, granted -> {
            if (granted) {
                onPermissionGrantedForImport();
            } else {
                getPermissionHelper().showPermissionRequiredDialog(context,
                        context.getString(R.string.update_storage_permission_required_title),
                        context.getString(R.string.storage_permission_required_to_import_settings),
                        this::onImportClicked);
            }
        });
    }

    private void onPermissionGrantedForImport() {
        String warningMessage = String.format(
                Locale.getDefault(),
                context.getString(R.string.import_warning_text),
                settingsFile.getParentFile().getPath(),
                settingsFile.getName()
                );

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.import_warning_title)
                .setMessage(warningMessage)
                .setPositiveButton(R.string.continue_text, (dialog1, which) -> onStartImportButtonClicked())
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();
    }

    private void onStartImportButtonClicked() {
        navigationController.presentController(loadingViewController);

        if (presenter != null) {
            presenter.doImport(settingsFile);
        }
    }

    @Override
    public void onSuccess(ImportExportRepository.ImportExport importExport) {
        // called on background thread

        if (context instanceof StartActivity) {
            AndroidUtils.runOnUiThread(() -> {
                if (importExport == ImportExportRepository.ImportExport.Import) {
                    ((StartActivity) context).restartApp();
                } else {
                    copyDirPathToClipboard();
                    clearAllChildControllers();

                    showMessage(String.format(Locale.getDefault(),
                            context.getString(R.string.successfully_exported_text),
                            settingsFile.getAbsolutePath()));

                    if (callbacks != null) {
                        callbacks.finish();
                    }
                }
            });
        }
    }

    private void copyDirPathToClipboard() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("exported_file_path", settingsFile.getPath());

        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    @Override
    public void onError(String message) {
        // may be called on background thread

        AndroidUtils.runOnUiThread(() -> {
            clearAllChildControllers();
            showMessage(message);
        });
    }

    private RuntimePermissionsHelper getPermissionHelper() {
        return ((StartActivity) context).getPermissionHelper();
    }

    private void showMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private void clearAllChildControllers() {
        if (loadingViewController.shown) {
            loadingViewController.stopPresenting();
        }
    }

    public interface OnExportSuccessCallbacks {
        void finish();
    }
}
