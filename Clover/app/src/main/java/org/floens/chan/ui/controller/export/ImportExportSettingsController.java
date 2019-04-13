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

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.presenter.ImportExportSettingsPresenter;
import org.floens.chan.core.repository.ImportExportRepository;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.utils.AndroidUtils;

public class ImportExportSettingsController extends SettingsController implements
        ImportExportSettingsPresenter.ImportExportSettingsCallbacks {

    @Nullable
    private ImportExportSettingsPresenter presenter;

    @Nullable
    private OnExportSuccessCallbacks callbacks;

    private LoadingViewController loadingViewController;

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
                    context.getString(R.string.export_settings_to_a_file), v -> {
                //TODO call directory picker to pick a directory where a file with pins will be stored

                navigationController.presentController(loadingViewController);

                if (presenter != null) {
                    presenter.doExport(context.getCacheDir());
                }
            }));

            group.add(new LinkSettingView(this,
                    context.getString(R.string.import_settings),
                    context.getString(R.string.import_settings_from_a_file), v -> {
                //TODO call file picker to pick a file with pins

                navigationController.presentController(loadingViewController);

                if (presenter != null) {
                    presenter.doImport(context.getCacheDir());
                }
            }));

            groups.add(group);
        }
    }

    //TODO: implement directory picker and file picker
    public ImagePickDelegate getImagePicker() {
        return ((StartActivity) context).getImagePickDelegate();
    }

    @Override
    public void onSuccess(ImportExportRepository.ImportExport importExport) {
        // called on background thread

        if (context instanceof StartActivity) {
            AndroidUtils.runOnUiThread(() -> {
                if (importExport == ImportExportRepository.ImportExport.Import) {
                    ((StartActivity) context).restartApp();
                } else {
                    onFailure("Exported successfully");

                    if (callbacks != null) {
                        clearAllChildControllers();
                        callbacks.onExported();
                    }
                }
            });
        }
    }

    private void clearAllChildControllers() {
        if (loadingViewController.shown) {
            loadingViewController.stopPresenting();
        }
    }

    @Override
    public void onFailure(String message) {
        // may be called on background thread

        AndroidUtils.runOnUiThread(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }

    public interface OnExportSuccessCallbacks {
        void onExported();
    }
}
