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
package com.github.adamantcheese.chan.ui.controller.export;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.presenter.ImportExportSettingsPresenter;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.io.File;
import java.util.Locale;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class ImportExportSettingsController extends SettingsController implements
        ImportExportSettingsPresenter.ImportExportSettingsCallbacks {
    public static final String EXPORT_FILE_NAME = getApplicationLabel() + "_exported_settings.json";

    @Nullable
    private ImportExportSettingsPresenter presenter;

    @Nullable
    private OnExportSuccessCallbacks callbacks;

    private LoadingViewController loadingViewController;
    private File settingsFile = new File(ChanSettings.saveLocation.get(), EXPORT_FILE_NAME);

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

        showCreateDirectoryDialog();
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

        ((StartActivity) context).getRuntimePermissionsHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (granted && presenter != null) {
                navigationController.presentController(loadingViewController);
                presenter.doExport(settingsFile);
            } else {
                ((StartActivity) context).getRuntimePermissionsHelper().showPermissionRequiredDialog(context,
                        context.getString(R.string.update_storage_permission_required_title),
                        context.getString(R.string.storage_permission_required_to_export_settings),
                        this::onExportClicked);
            }
        });
    }

    private void showCreateDirectoryDialog() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.error_external_storage_is_not_mounted));
            return;
        }

        // if we already have the permission and the default directory already exists - do not show
        // the dialog again
        if (((StartActivity) context).getRuntimePermissionsHelper().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (settingsFile.getParentFile().exists()) {
                return;
            }
        }

        // Ask the user's permission to check whether the default directory exists and create it if it doesn't
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.default_directory_may_not_exist_title))
                .setMessage(context.getString(R.string.default_directory_may_not_exist_message))
                .setPositiveButton(context.getString(R.string.create), (dialog1, which) -> ((StartActivity) context).getRuntimePermissionsHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
                    if (granted) {
                        onPermissionGrantedForDirectoryCreation();
                    }
                }))
                .setNegativeButton(context.getString(R.string.do_not), null)
                .create()
                .show();
    }

    private void onPermissionGrantedForDirectoryCreation() {
        if (settingsFile.getParentFile().exists()) {
            return;
        }

        if (!settingsFile.getParentFile().mkdirs()) {
            showMessage(context.getString(R.string.could_not_create_dir_for_export_error_text, settingsFile.getParentFile().getAbsolutePath()));
        }
    }

    private void onImportClicked() {
        String state = Environment.getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            showMessage(context.getString(R.string.error_external_storage_is_not_mounted));
            return;
        }

        ((StartActivity) context).getRuntimePermissionsHelper().requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE, granted -> {
            if (granted) {
                onPermissionGrantedForImport();
            } else {
                ((StartActivity) context).getRuntimePermissionsHelper().showPermissionRequiredDialog(context,
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
        if (presenter != null) {
            navigationController.presentController(loadingViewController);
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
