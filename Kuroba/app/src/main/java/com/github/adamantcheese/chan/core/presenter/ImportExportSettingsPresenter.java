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
package com.github.adamantcheese.chan.core.presenter;

import static com.github.adamantcheese.chan.Chan.inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.k1rakishou.fsaf.file.ExternalFile;

import javax.inject.Inject;

public class ImportExportSettingsPresenter {
    @Nullable
    private ImportExportSettingsCallbacks callbacks;

    @Inject
    ImportExportRepository importExportRepository;

    public ImportExportSettingsPresenter(@NonNull ImportExportSettingsCallbacks callbacks) {
        inject(this);
        this.callbacks = callbacks;
    }

    public void onDestroy() {
        this.callbacks = null;
    }

    public void doExport(ExternalFile settingsFile, boolean isNewFile) {
        importExportRepository.exportTo(settingsFile, isNewFile, new ImportExportRepository.ImportExportCallbacks() {
            @Override
            public void onSuccess(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onSuccess(importExport);
                }
            }

            @Override
            public void onNothingToImportExport(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onError("There is nothing to export");
                }
            }

            @Override
            public void onError(Throwable error, ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onError("Error while trying to export settings = " + error.getMessage());
                }
            }
        });
    }

    public void doImport(ExternalFile settingsFile) {
        importExportRepository.importFrom(settingsFile, new ImportExportRepository.ImportExportCallbacks() {
            @Override
            public void onSuccess(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onSuccess(importExport);
                }
            }

            @Override
            public void onNothingToImportExport(ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onError("There is nothing to import");
                }
            }

            @Override
            public void onError(Throwable error, ImportExportRepository.ImportExport importExport) {
                //called on background thread

                if (callbacks != null) {
                    callbacks.onError("Error while trying to import settings = " + error.getMessage());
                }
            }
        });
    }

    public interface ImportExportSettingsCallbacks {
        void onSuccess(ImportExportRepository.ImportExport importExport);

        void onError(String message);
    }
}
