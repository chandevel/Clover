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
package org.floens.chan.core.presenter;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.repository.ImportExportRepository;

import java.io.File;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class ImportExportSettingsPresenter {
    @Nullable
    private ImportSettingsCallbacks importCallbacks;
    @Nullable
    private ExportSettingsCallbacks exportCallbacks;

    @Inject
    ImportExportRepository importExportRepository;

    public ImportExportSettingsPresenter(
            @NonNull ImportSettingsCallbacks importCallbacks,
            @NonNull ExportSettingsCallbacks exportCallbacks
    ) {
        inject(this);
        this.importCallbacks = importCallbacks;
        this.exportCallbacks = exportCallbacks;
    }

    public void onDestroy() {
        this.importCallbacks = null;
        this.exportCallbacks = null;
    }

    public void doExport(File settingsFile) {
        importExportRepository.exportTo(settingsFile, new ImportExportRepository.ExportCallbacks() {

            @Override
            public void onSuccessExport() {
                //called on background thread

                if (exportCallbacks != null) {
                    exportCallbacks.onSuccessExport();
                }
            }

            @Override
            public void onNothingToExport() {
                //called on background thread

                if (exportCallbacks != null) {
                    exportCallbacks.onError("There is nothing to export");
                }
            }

            @Override
            public void onExportError(Throwable error) {
                //called on background thread

                if (exportCallbacks != null) {
                    exportCallbacks.onError("Error while trying to export settings: " + error.getMessage());
                }
            }
        });
    }

    public void doImport(File settingsFile) {
        importExportRepository.importFrom(settingsFile, new ImportExportRepository.ImportCallbacks() {

            @Override
            public void onSuccessImport() {
                //called on background thread

                if (importCallbacks != null) {
                    importCallbacks.onSuccessImport();
                }
            }

            @Override
            public void onNothingToImport() {
                //called on background thread

                if (importCallbacks != null) {
                    importCallbacks.onError("There is nothing to import");
                }
            }

            @Override
            public void onImportError(Throwable error) {
                //called on background thread

                if (importCallbacks != null) {
                    importCallbacks.onError("Error while trying to import settings: " + error.getMessage());
                }
            }
        });
    }

    public interface ImportSettingsCallbacks {
        void onSuccessImport();

        void onError(String message);
    }

    public interface ExportSettingsCallbacks {
        void onSuccessExport();

        void onError(String message);
    }
}
