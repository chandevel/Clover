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

        if (!createExportDirectoryIfNotExist()) {
            showMessage(context.getString(R.string.could_not_create_dir_for_export_error_text));
        }
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
            showMessage(context.getString(R.string.cannot_export_error_text));
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

    private boolean createExportDirectoryIfNotExist() {
        if (!settingsFile.getParentFile().exists()) {
            return settingsFile.getParentFile().mkdirs();
        }

        return true;
    }

    private void onImportClicked() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.cannot_import_error_text));
            return;
        }

        getPermissionHelper().requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, granted -> {
            if (granted) {
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
            } else {
                getPermissionHelper().showPermissionRequiredDialog(context,
                        context.getString(R.string.update_storage_permission_required_title),
                        context.getString(R.string.storage_permission_required_to_import_settings),
                        this::onImportClicked);
            }
        });
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

                    if (callbacks != null) {
                        clearAllChildControllers();
                        callbacks.onExported();
                    }

                    showMessage(String.format(Locale.getDefault(),
                            context.getString(R.string.successfully_exported_text),
                            settingsFile.getAbsolutePath()));
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
        void onExported();
    }
}
