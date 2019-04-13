package org.floens.chan.ui.controller;

import android.content.Context;
import android.support.annotation.Nullable;

import org.floens.chan.R;
import org.floens.chan.core.presenter.ImportExportSettingsPresenter;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

public class ImportExportSettingsController extends SettingsController implements
        ImportExportSettingsPresenter.ExportSettingsCallbacks,
        ImportExportSettingsPresenter.ImportSettingsCallbacks {

    @Nullable
    private ImportExportSettingsPresenter presenter;

    public ImportExportSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_import_export);

        presenter = new ImportExportSettingsPresenter(this, this);

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
    }

    protected void setupLayout() {
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);
    }

    private void populatePreferences() {
        // Import/export pins group
        {
            SettingsGroup pins = new SettingsGroup("Import/Export pins");

            pins.add(new LinkSettingView(this, "Export pins", "Export pins to a file", v -> {
                //TODO call directory picker to pick a directory where a file with pins will be stored

                if (presenter != null) {
                    presenter.doExport(context.getCacheDir());
                }
            }));

            pins.add(new LinkSettingView(this, "Import pins", "Import pins from a file", v -> {
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
    public void onImportedSuccessfully() {

    }

    @Override
    public void onExportedSuccessfully() {

    }

    @Override
    public void onNothingToExport() {

    }

    @Override
    public void onError(Throwable error) {

    }
}
