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

    public ImportExportSettingsController(Context context, @NonNull OnExportSuccessCallbacks callbacks) {
        super(context);

        this.callbacks = callbacks;
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
            SettingsGroup pins = new SettingsGroup("Import/Export settings");

            pins.add(new LinkSettingView(this, "Export settings", "Export settings to a file", v -> {
                //TODO call directory picker to pick a directory where a file with pins will be stored

                if (presenter != null) {
                    presenter.doExport(context.getCacheDir());
                }
            }));

            pins.add(new LinkSettingView(this, "Import settings", "Import settings from a file", v -> {
                //TODO call file picker to pick a file with pins

                if (presenter != null) {
                    presenter.doImport(context.getCacheDir());
                }
            }));

            groups.add(pins);
        }
    }

    public ImagePickDelegate getImagePicker() {
        return ((StartActivity) context).getImagePickDelegate();
    }

    @Override
    public void onSuccess(ImportExportRepository.ImportExport importExport) {
        // called on background thread

        if (context instanceof StartActivity) {
            AndroidUtils.runOnUiThread(() ->{
                if (importExport == ImportExportRepository.ImportExport.Import) {
                    ((StartActivity) context).restartApp();
                } else {
                    showToast("Exported successfully");

                    if (callbacks != null) {
                        clearAllChildControllers();
                        callbacks.onExported();
                    }
                }
            });
        }
    }

    private void clearAllChildControllers() {
        //TODO
    }

    @Override
    public void showToast(String message) {
        // may be called on background thread

        AndroidUtils.runOnUiThread(() -> {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }

    public interface OnExportSuccessCallbacks {
        void onExported();
    }
}
