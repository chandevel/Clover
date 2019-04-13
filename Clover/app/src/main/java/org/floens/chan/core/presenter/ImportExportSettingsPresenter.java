package org.floens.chan.core.presenter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.floens.chan.core.repository.ImportExportRepository;

import java.io.File;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class ImportExportSettingsPresenter {
    @Nullable
    private ExportSettingsCallbacks exportCallbacks;

    @Nullable
    private ImportSettingsCallbacks importCallbacks;

    @Inject
    ImportExportRepository importExportRepository;

    public ImportExportSettingsPresenter(
            @NonNull ExportSettingsCallbacks exportCallbacks,
            @NonNull ImportSettingsCallbacks importCallbacks
    ) {
        inject(this);
        this.exportCallbacks = exportCallbacks;
        this.importCallbacks = importCallbacks;
    }

    public void onDestroy() {
        this.exportCallbacks = null;
        this.importCallbacks = null;
    }

    public void doExport(File cacheDir) {
        importExportRepository.exportTo(cacheDir, new ImportExportRepository.ExportCallbacks() {
            @Override
            public void onExportedSuccessfully() {
                if (exportCallbacks != null) {
                    exportCallbacks.onExportedSuccessfully();
                }
            }

            @Override
            public void onNothingToExport() {
                if (exportCallbacks != null) {
                    exportCallbacks.onNothingToExport();
                }
            }

            @Override
            public void onError(Throwable error) {
                if (exportCallbacks != null) {
                    exportCallbacks.onError(error);
                }
            }
        });
    }

    public void doImport(File cacheDir) {
        importExportRepository.importFrom(cacheDir, new ImportExportRepository.ImportCallbacks() {
            @Override
            public void onImportedSuccessfully() {
                if (importCallbacks != null) {
                    importCallbacks.onImportedSuccessfully();
                }
            }

            @Override
            public void onError(Throwable error) {
                if (importCallbacks != null) {
                    importCallbacks.onError(error);
                }
            }
        });
    }

    public interface ExportSettingsCallbacks {
        void onExportedSuccessfully();

        void onNothingToExport();

        void onError(Throwable error);
    }

    public interface ImportSettingsCallbacks {
        void onImportedSuccessfully();

        void onError(Throwable error);
    }
}
