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
package com.github.adamantcheese.chan.core.repository;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.export.ExportedAppSettings;
import com.github.adamantcheese.chan.core.model.export.ExportedBoard;
import com.github.adamantcheese.chan.core.model.export.ExportedFilter;
import com.github.adamantcheese.chan.core.model.export.ExportedLoadable;
import com.github.adamantcheese.chan.core.model.export.ExportedPin;
import com.github.adamantcheese.chan.core.model.export.ExportedPostHide;
import com.github.adamantcheese.chan.core.model.export.ExportedSavedThread;
import com.github.adamantcheese.chan.core.model.export.ExportedSite;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;
import com.j256.ormlite.support.ConnectionSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

public class ImportExportRepository {
    private static final String TAG = "ImportExportRepository";

    // Don't forget to change this when changing any of the Export models.
    // Also, don't forget to handle the change in the onUpgrade or onDowngrade methods
    public static final int CURRENT_EXPORT_SETTINGS_VERSION = 4;

    private DatabaseManager databaseManager;
    private DatabaseHelper databaseHelper;
    private Gson gson;

    @Inject
    public ImportExportRepository(
            DatabaseManager databaseManager,
            DatabaseHelper databaseHelper,
            Gson gson
    ) {
        this.databaseManager = databaseManager;
        this.databaseHelper = databaseHelper;
        this.gson = gson;
    }

    public void exportTo(File settingsFile, ImportExportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            try {
                ExportedAppSettings appSettings = readSettingsFromDatabase();
                if (appSettings.isEmpty()) {
                    callbacks.onNothingToImportExport(ImportExport.Export);
                    return null;
                }

                String json = gson.toJson(appSettings);

                if (settingsFile.exists()) {
                    if (!settingsFile.isFile()) {
                        throw new IOException(
                                "Settings file is not a file (???) " + settingsFile.getAbsolutePath()
                        );
                    }

                    if (!settingsFile.delete()) {
                        Logger.w(TAG, "Could not delete export file before exporting " + settingsFile.getAbsolutePath());
                    }
                }

                if (!settingsFile.createNewFile()) {
                    throw new IOException(
                            "Could not create a file for exporting " + settingsFile.getAbsolutePath()
                    );
                }

                if (!settingsFile.exists() || !settingsFile.canWrite()) {
                    throw new IOException(
                            "Something wrong with export file (Can't write or it doesn't exist) "
                                    + settingsFile.getAbsolutePath()
                    );
                }

                try (FileWriter writer = new FileWriter(settingsFile)) {
                    writer.write(json);
                }

                Logger.d(TAG, "Exporting done!");
                callbacks.onSuccess(ImportExport.Export);
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to export settings", error);

                deleteExportFile(settingsFile);
                callbacks.onError(error, ImportExport.Export);
            }

            return null;
        });
    }

    public void importFrom(File settingsFile, ImportExportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            try {
                if (!settingsFile.exists()) {
                    Logger.i(TAG, "There is nothing to import, importFile does not exist " + settingsFile.getAbsolutePath());
                    callbacks.onNothingToImportExport(ImportExport.Import);
                    return null;
                }

                if (!settingsFile.canRead()) {
                    throw new IOException(
                            "Something wrong with import file (Can't read or it doesn't exist) "
                                    + settingsFile.getAbsolutePath()
                    );
                }

                ExportedAppSettings appSettings;

                try (FileReader reader = new FileReader(settingsFile)) {
                    appSettings = gson.fromJson(reader, ExportedAppSettings.class);
                }

                if (appSettings.isEmpty()) {
                    Logger.i(TAG, "There is nothing to import, appSettings is empty");
                    callbacks.onNothingToImportExport(ImportExport.Import);
                    return null;
                }

                writeSettingsToDatabase(appSettings);

                Logger.d(TAG, "Importing done!");
                callbacks.onSuccess(ImportExport.Import);
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to import settings", error);
                callbacks.onError(error, ImportExport.Import);
            }

            return null;
        });
    }

    private void deleteExportFile(File exportFile) {
        if (exportFile != null) {
            if (!exportFile.delete()) {
                Logger.w(TAG, "Could not delete export file " + exportFile.getAbsolutePath());
            }
        }
    }

    private void writeSettingsToDatabase(@NonNull ExportedAppSettings appSettingsParam)
            throws SQLException, IOException, DowngradeNotSupportedException {
        ExportedAppSettings appSettings = appSettingsParam;

        if (appSettings.getVersion() < CURRENT_EXPORT_SETTINGS_VERSION) {
            appSettings = onUpgrade(appSettings.getVersion(), appSettings);
        } else if (appSettings.getVersion() > CURRENT_EXPORT_SETTINGS_VERSION) {
            // we don't support settings downgrade so just notify the user about it
            throw new DowngradeNotSupportedException("You are attempting to import settings with " +
                    "version higher than the current app's settings version (downgrade). " +
                    "This is not supported so nothing will be imported."
            );
        }

        // recreate tables from scratch, because we need to reset database IDs as well
        try (ConnectionSource cs = databaseHelper.getConnectionSource()) {
            databaseHelper.dropTables(cs);
            databaseHelper.createTables(cs);
        }

        for (ExportedBoard exportedBoard : appSettings.getExportedBoards()) {
            assert exportedBoard.getDescription() != null;
            databaseHelper.boardsDao.createIfNotExists(new Board(
                    exportedBoard.getSiteId(),
                    exportedBoard.isSaved(),
                    exportedBoard.getOrder(),
                    exportedBoard.getName(),
                    exportedBoard.getCode(),
                    exportedBoard.isWorkSafe(),
                    exportedBoard.getPerPage(),
                    exportedBoard.getPages(),
                    exportedBoard.getMaxFileSize(),
                    exportedBoard.getMaxWebmSize(),
                    exportedBoard.getMaxCommentChars(),
                    exportedBoard.getBumpLimit(),
                    exportedBoard.getImageLimit(),
                    exportedBoard.getCooldownThreads(),
                    exportedBoard.getCooldownReplies(),
                    exportedBoard.getCooldownImages(),
                    exportedBoard.isSpoilers(),
                    exportedBoard.getCustomSpoilers(),
                    exportedBoard.isUserIds(),
                    exportedBoard.isCodeTags(),
                    exportedBoard.isPreuploadCaptcha(),
                    exportedBoard.isCountryFlags(),
                    exportedBoard.isMathTags(),
                    exportedBoard.getDescription(),
                    exportedBoard.isArchive()
            ));
        }

        for (ExportedSite exportedSite : appSettings.getExportedSites()) {
            SiteModel inserted = databaseHelper.siteDao.createIfNotExists(new SiteModel(
                    exportedSite.getSiteId(),
                    exportedSite.getConfiguration(),
                    exportedSite.getUserSettings(),
                    exportedSite.getOrder()
            ));

            List<ExportedSavedThread> exportedSavedThreads = appSettings.getExportedSavedThreads();

            for (ExportedPin exportedPin : exportedSite.getExportedPins()) {
                ExportedLoadable exportedLoadable = exportedPin.getExportedLoadable();
                if (exportedLoadable == null) continue;

                Loadable loadable = Loadable.importLoadable(
                        inserted.id,
                        exportedLoadable.getMode(),
                        exportedLoadable.getBoardCode(),
                        exportedLoadable.getNo(),
                        exportedLoadable.getTitle(),
                        exportedLoadable.getListViewIndex(),
                        exportedLoadable.getListViewTop(),
                        exportedLoadable.getLastViewed(),
                        exportedLoadable.getLastLoaded()
                );

                Loadable insertedLoadable = databaseHelper.loadableDao.createIfNotExists(loadable);
                ExportedSavedThread exportedSavedThread = findSavedThreadByOldLoadableId(
                        exportedSavedThreads,
                        (int) exportedLoadable.getLoadableId());

                // ExportedSavedThreads may have their loadable ids noncontiguous. Like 1,3,4,5,21,152.
                // SQLite does not like it and will be returning to us contiguous ids ignoring our ids.
                // This will create a situation where savedThread.loadableId may not have a loadable.
                // So we need to fix this by finding a saved thread by old loadable id and updating
                // it's loadable id with the newly inserted id.
                if (exportedSavedThread != null) {
                    exportedSavedThread.loadableId = insertedLoadable.id;

                    databaseHelper.savedThreadDao.createIfNotExists(new SavedThread(
                            exportedSavedThread.isFullyDownloaded,
                            exportedSavedThread.isStopped,
                            exportedSavedThread.lastSavedPostNo,
                            exportedSavedThread.loadableId
                    ));
                }

                Pin pin = new Pin(
                        insertedLoadable,
                        exportedPin.isWatching(),
                        exportedPin.getWatchLastCount(),
                        exportedPin.getWatchNewCount(),
                        exportedPin.getQuoteLastCount(),
                        exportedPin.getQuoteNewCount(),
                        exportedPin.isError(),
                        exportedPin.getThumbnailUrl(),
                        exportedPin.getOrder(),
                        exportedPin.isArchived(),
                        exportedPin.getPinType()
                );
                databaseHelper.pinDao.createIfNotExists(pin);
            }
        }

        for (ExportedFilter exportedFilter : appSettings.getExportedFilters()) {
            databaseHelper.filterDao.createIfNotExists(new Filter(
                    exportedFilter.isEnabled(),
                    exportedFilter.getType(),
                    exportedFilter.getPattern(),
                    exportedFilter.isAllBoards(),
                    exportedFilter.getBoards(),
                    exportedFilter.getAction(),
                    exportedFilter.getColor(),
                    exportedFilter.getApplyToReplies(),
                    exportedFilter.getOrder(),
                    exportedFilter.getOnlyOnOP(),
                    exportedFilter.getApplyToSaved()
            ));
        }

        for (ExportedPostHide exportedPostHide : appSettings.getExportedPostHides()) {
            databaseHelper.postHideDao.createIfNotExists(new PostHide(
                    exportedPostHide.getSite(),
                    exportedPostHide.getBoard(),
                    exportedPostHide.getNo()
            ));
        }

        ChanSettings.deserializeFromString(appSettingsParam.getSettings());
    }

    @Nullable
    private ExportedSavedThread findSavedThreadByOldLoadableId(
            List<ExportedSavedThread> exportedSavedThreads,
            int oldLoadableId) {
        for (ExportedSavedThread exportedSavedThread : exportedSavedThreads) {
            if (exportedSavedThread.loadableId == oldLoadableId) {
                return exportedSavedThread;
            }
        }

        return null;
    }

    private ExportedAppSettings onUpgrade(int version, ExportedAppSettings appSettings) {
        if (version < 2) {
            //clear the post hides for version 1, threadNo field was added
            appSettings.setExportedPostHides(new ArrayList<>());
        }

        if (version < 3) {
            //clear the site model usersettings to be an empty JSON map for version 2, as they won't parse correctly otherwise
            for (ExportedSite site : appSettings.getExportedSites()) {
                site.setUserSettings("{}");
            }
        }

        if (version < 4) {
            //55chan and 8chan were removed for this version
            ExportedSite chan8 = null;
            ExportedSite chan55 = null;
            for (ExportedSite site : appSettings.getExportedSites()) {
                SiteConfig config = gson.fromJson(site.getConfiguration(), SiteConfig.class);
                if (config.classId == 1 && chan8 == null) chan8 = site;
                if (config.classId == 7 && chan55 == null) chan55 = site;
            }

            if (chan55 != null) {
                deleteExportedSite(chan55, appSettings);
            }

            if (chan8 != null) {
                deleteExportedSite(chan8, appSettings);
            }
        }
        return appSettings;
    }

    @NonNull
    private ExportedAppSettings readSettingsFromDatabase() throws java.sql.SQLException, IOException {
        @SuppressLint("UseSparseArrays")
        Map<Integer, SiteModel> sitesMap = new HashMap<>();
        {
            List<SiteModel> sites = databaseHelper.siteDao.queryForAll();

            for (SiteModel site : sites) {
                sitesMap.put(site.id, site);
            }
        }

        @SuppressLint("UseSparseArrays")
        Map<Integer, Loadable> loadableMap = new HashMap<>();
        {
            List<Loadable> loadables = databaseHelper.loadableDao.queryForAll();

            for (Loadable loadable : loadables) {
                loadableMap.put(loadable.id, loadable);
            }
        }

        Set<Pin> pins = new HashSet<>(databaseHelper.pinDao.queryForAll());
        Map<SiteModel, List<ExportedPin>> toExportMap = new HashMap<>();

        for (SiteModel siteModel : sitesMap.values()) {
            toExportMap.put(siteModel, new ArrayList<>());
        }

        for (Pin pin : pins) {
            Loadable loadable = loadableMap.get(pin.loadable.id);
            if (loadable == null) {
                throw new NullPointerException("Could not find Loadable by pin.loadable.id " + pin.loadable.id);
            }

            SiteModel siteModel = sitesMap.get(loadable.siteId);
            if (siteModel == null) {
                throw new NullPointerException("Could not find siteModel by loadable.siteId " + loadable.siteId);
            }

            ExportedLoadable exportedLoadable = new ExportedLoadable(
                    loadable.boardCode,
                    loadable.id,
                    loadable.lastLoaded,
                    loadable.lastViewed,
                    loadable.listViewIndex,
                    loadable.listViewTop,
                    loadable.mode,
                    loadable.no,
                    loadable.siteId,
                    loadable.title
            );

            ExportedPin exportedPin = new ExportedPin(
                    pin.archived,
                    pin.id,
                    pin.isError,
                    loadable.id,
                    pin.order,
                    pin.quoteLastCount,
                    pin.quoteNewCount,
                    pin.thumbnailUrl,
                    pin.watchLastCount,
                    pin.watchNewCount,
                    pin.watching,
                    exportedLoadable,
                    pin.pinType
            );

            toExportMap.get(siteModel).add(exportedPin);
        }

        List<ExportedSite> exportedSites = new ArrayList<>();

        for (Map.Entry<SiteModel, List<ExportedPin>> entry : toExportMap.entrySet()) {
            ExportedSite exportedSite = new ExportedSite(
                    entry.getKey().id,
                    entry.getKey().configuration,
                    entry.getKey().order,
                    entry.getKey().userSettings,
                    entry.getValue()
            );

            exportedSites.add(exportedSite);
        }

        List<ExportedBoard> exportedBoards = new ArrayList<>();

        for (Board board : databaseHelper.boardsDao.queryForAll()) {
            exportedBoards.add(new ExportedBoard(
                    board.siteId,
                    board.saved,
                    board.order,
                    board.name,
                    board.code,
                    board.workSafe,
                    board.perPage,
                    board.pages,
                    board.maxFileSize,
                    board.maxWebmSize,
                    board.maxCommentChars,
                    board.bumpLimit,
                    board.imageLimit,
                    board.cooldownThreads,
                    board.cooldownReplies,
                    board.cooldownImages,
                    board.spoilers,
                    board.customSpoilers,
                    board.userIds,
                    board.codeTags,
                    board.preuploadCaptcha,
                    board.countryFlags,
                    board.mathTags,
                    board.description,
                    board.archive
            ));
        }

        List<ExportedFilter> exportedFilters = new ArrayList<>();

        for (Filter filter : databaseHelper.filterDao.queryForAll()) {
            exportedFilters.add(new ExportedFilter(
                    filter.enabled,
                    filter.type,
                    filter.pattern,
                    filter.allBoards,
                    filter.boards,
                    filter.action,
                    filter.color,
                    filter.applyToReplies,
                    filter.order,
                    filter.onlyOnOP,
                    filter.applyToSaved
            ));
        }

        List<ExportedPostHide> exportedPostHides = new ArrayList<>();

        for (PostHide threadHide : databaseHelper.postHideDao.queryForAll()) {
            exportedPostHides.add(new ExportedPostHide(
                    threadHide.site,
                    threadHide.board,
                    threadHide.no,
                    threadHide.wholeThread,
                    threadHide.hide,
                    threadHide.hideRepliesToThisPost,
                    threadHide.threadNo
            ));
        }

        List<ExportedSavedThread> exportedSavedThreads = new ArrayList<>();

        for (SavedThread savedThread : databaseHelper.savedThreadDao.queryForAll()) {
            exportedSavedThreads.add(new ExportedSavedThread(
                    savedThread.loadableId,
                    savedThread.lastSavedPostNo,
                    savedThread.isFullyDownloaded,
                    savedThread.isStopped
            ));
        }

        String settings = ChanSettings.serializeToString();

        return new ExportedAppSettings(
                exportedSites,
                exportedBoards,
                exportedFilters,
                exportedPostHides,
                exportedSavedThreads,
                settings
        );
    }

    public enum ImportExport {
        Import,
        Export
    }

    public interface ImportExportCallbacks {
        void onSuccess(ImportExport importExport);

        void onNothingToImportExport(ImportExport importExport);

        void onError(Throwable error, ImportExport importExport);
    }

    public static class DowngradeNotSupportedException extends Exception {
        public DowngradeNotSupportedException(String message) {
            super(message);
        }
    }

    private void deleteExportedSite(ExportedSite site, ExportedAppSettings appSettings) {
        //filters
        List<ExportedFilter> filtersToDelete = new ArrayList<>();
        for (ExportedFilter filter : appSettings.getExportedFilters()) {
            if (filter.isAllBoards() || TextUtils.isEmpty(filter.getBoards())) {
                continue;
            }

            for (String uniqueId : filter.getBoards().split(",")) {
                String[] split = uniqueId.split(":");
                if (split.length == 2 && Integer.parseInt(split[0]) == site.getSiteId()) {
                    filtersToDelete.add(filter);
                    break;
                }
            }
        }
        appSettings.getExportedFilters().removeAll(filtersToDelete);

        //boards
        List<ExportedBoard> boardsToDelete = new ArrayList<>();
        for (ExportedBoard board : appSettings.getExportedBoards()) {
            if (board.getSiteId() == site.getSiteId()) {
                boardsToDelete.add(board);
            }
        }
        appSettings.getExportedBoards().removeAll(boardsToDelete);

        //loadables for saved threads
        List<ExportedLoadable> loadables = new ArrayList<>();
        for (ExportedPin pin : site.getExportedPins()) {
            if (pin.getExportedLoadable().getSiteId() == site.getSiteId()) {
                loadables.add(pin.getExportedLoadable());
            }
        }

        if (!loadables.isEmpty()) {
            List<ExportedSavedThread> savedThreadToDelete = new ArrayList<>();
            for (ExportedLoadable loadable : loadables) {
                //saved threads
                for (ExportedSavedThread savedThread : appSettings.getExportedSavedThreads()) {
                    if (loadable.getLoadableId() == savedThread.getLoadableId()) {
                        savedThreadToDelete.add(savedThread);
                    }
                }
            }
            appSettings.getExportedSavedThreads().removeAll(savedThreadToDelete);
        }

        //post hides
        List<ExportedPostHide> hidesToDelete = new ArrayList<>();
        for (ExportedPostHide hide : appSettings.getExportedPostHides()) {
            if (hide.getSite() == site.getSiteId()) {
                hidesToDelete.add(hide);
            }
        }
        appSettings.getExportedPostHides().removeAll(hidesToDelete);

        //site (also removes pins and loadables)
        appSettings.getExportedSites().remove(site);
    }
}
