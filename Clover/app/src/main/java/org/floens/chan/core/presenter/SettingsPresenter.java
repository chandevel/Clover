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

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.repository.ImportExportRepository;
import org.floens.chan.core.settings.ChanSettings;

import javax.inject.Inject;

public class SettingsPresenter {
    private Callback callback;

    private DatabaseManager databaseManager;

    // Inject ImportExportRepository here to avoid strange behavior when CURRENT_EXPORT_SETTINGS_VERSION is zero
    // even though it is a static variable that is initialized with non-zero value. (Apparently it happens
    // because if we don't inject the class here it won't be constructed yet, so the
    // CURRENT_EXPORT_SETTINGS_VERSION holds a default value - zero)
    private ImportExportRepository importExportRepository;

    @Inject
    public SettingsPresenter(DatabaseManager databaseManager, ImportExportRepository importExportRepository) {
        this.databaseManager = databaseManager;
        this.importExportRepository = importExportRepository;
    }

    public void create(Callback callback) {
        this.callback = callback;
    }

    public void destroy() {

    }

    public void show() {
        long siteCount = databaseManager.runTask(
                databaseManager.getDatabaseSiteManager().getCount());
        long filterCount = databaseManager.runTask(
                databaseManager.getDatabaseFilterManager().getCount());

        callback.setSiteCount((int) siteCount);
        callback.setFiltersCount((int) filterCount);
        callback.setWatchEnabled(ChanSettings.watchEnabled.get());

        suggestToReexportSettings();
    }

    /**
     * When settings file version changes - show the user a suggestion to re-export the settings file
     * */
    private void suggestToReexportSettings() {
        int lastVersionSuggestion = ChanSettings.suggestReexportSettingsLastVersion.get();

        if (ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION > lastVersionSuggestion
                && lastVersionSuggestion != -1) {
            callback.showReexportSuggestionMessage();
        }

        ChanSettings.suggestReexportSettingsLastVersion.set(ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION);
    }

    public interface Callback {
        void setSiteCount(int count);

        void setFiltersCount(int count);

        void setWatchEnabled(boolean enabled);

        void showReexportSuggestionMessage();
    }
}
