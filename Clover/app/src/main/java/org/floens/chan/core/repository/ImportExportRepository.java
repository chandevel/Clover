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
package org.floens.chan.core.repository;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.export.ExportedAppSettings;
import org.floens.chan.core.model.export.ExportedBoard;
import org.floens.chan.core.model.export.ExportedFilter;
import org.floens.chan.core.model.export.ExportedLoadable;
import org.floens.chan.core.model.export.ExportedPin;
import org.floens.chan.core.model.export.ExportedPostHide;
import org.floens.chan.core.model.export.ExportedSite;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Filter;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.model.orm.SiteModel;
import org.floens.chan.core.model.orm.ThreadHide;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.ByteUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;

public class ImportExportRepository {
    private static final String TAG = "ImportExportRepository";
    private static final byte[] SETTINGS_HEADER_MAGIC_CONST = "CLOVER_SETTINGS".getBytes();
    private static final int SETTINGS_HEADER_MAGIC_CONST_LEN = SETTINGS_HEADER_MAGIC_CONST.length;
    public static final String EXPORT_FILE_NAME = "exported_clover_settings.json";

    // Don't forget to change this when changing any of the Export models.
    // Also, don't forget to handle the change in the onUpgrade method.
    public static final int CURRENT_EXPORT_SETTINGS_VERSION = 2;

    private DatabaseManager databaseManager;
    private DatabaseHelper databaseHelper;
    private Gson gson;

    private AtomicBoolean exportingRunning = new AtomicBoolean(false);
    private AtomicBoolean importingRunning = new AtomicBoolean(false);

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

    public void exportTo(File settingsFile, ExportCallbacks callbacks) {
        if (!exportingRunning.compareAndSet(false, true)) {
            return;
        }

        databaseManager.runTaskAsync(() -> {
            try {
                ExportedAppSettings appSettings = readSettingsFromDatabase();
                if (appSettings.isEmpty()) {
                    callbacks.onNothingToExport();
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

                try (RandomAccessFile raf = new RandomAccessFile(settingsFile, "rw")) {
                    // Write the header magic constant so we can figure out that this file is the settings file
                    raf.write(SETTINGS_HEADER_MAGIC_CONST);
                    // Write the version of the file
                    raf.writeInt(ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION);
                    // Write the settings
                    raf.writeUTF(json);
                }

                Logger.d(TAG, "Exporting done!");
                callbacks.onSuccessExport();
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to export settings", error);

                deleteExportFile(settingsFile);
                callbacks.onExportError(error);

                // throw to upstream to rollback the transaction
                throw error;
            } finally {
                exportingRunning.set(false);
            }

            return null;
        });
    }

    public void importFrom(File settingsFile, ImportCallbacks callbacks) {
        if (!importingRunning.compareAndSet(false, true)) {
            return;
        }

        databaseManager.runTaskAsync(() -> {
            try {
                if (!settingsFile.exists()) {
                    Logger.d(TAG, "There is nothing to import, importFile does not exist " +
                            settingsFile.getAbsolutePath());

                    callbacks.onNothingToImport();
                    return null;
                }

                if (!settingsFile.canRead()) {
                    throw new IOException(
                            "Something wrong with import file (Can't read or it doesn't exist) "
                                    + settingsFile.getAbsolutePath()
                    );
                }

                ExportedAppSettings appSettings;
                int settingsFileVersion;

                try (RandomAccessFile raf = new RandomAccessFile(settingsFile, "r")) {
                    byte[] readMagicConst = new byte[SETTINGS_HEADER_MAGIC_CONST_LEN];
                    int readCount = raf.read(readMagicConst);

                    if (readCount != SETTINGS_HEADER_MAGIC_CONST_LEN) {
                        throw new IOException("Could not read settings header magic constant (the " +
                                "amount of bytes read is not equals to the header const length: " + readCount + ")");
                    }

                    if (!Arrays.equals(readMagicConst, SETTINGS_HEADER_MAGIC_CONST)) {
                        throw new IOException("The file does not contain the settings header magic constant, " +
                                "file header is: " + ByteUtils.bytesToHex(readMagicConst) +
                                ", but should be: " + ByteUtils.bytesToHex(SETTINGS_HEADER_MAGIC_CONST));
                    }

                    settingsFileVersion = raf.readInt();
                    Logger.d(TAG, "Settings file version = " + settingsFileVersion);

                    throwIfDowngrade(settingsFileVersion);
                    throwIfCannotUpgrade(settingsFileVersion);

                    appSettings = gson.fromJson(raf.readUTF(), ExportedAppSettings.class);
                }

                if (appSettings.isEmpty()) {
                    Logger.d(TAG, "There is nothing to import, appSettings is empty");
                    callbacks.onNothingToImport();
                    return null;
                }

                writeSettingsToDatabase(settingsFileVersion, appSettings);

                Logger.d(TAG, "Importing success!");
                callbacks.onSuccessImport();
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to import settings", error);
                callbacks.onImportError(error);

                // throw to upstream to rollback the transaction
                throw error;
            } finally {
                importingRunning.set(false);
            }

            return null;
        });
    }

    private void throwIfCannotUpgrade(int settingsFileVersion) throws UpgradeImpossibleException {
        if (settingsFileVersion == 1) {
            ///////////////////////////////////////
            // Upgrade from version 1.
            // In version 2 we had to change the settings file format (Version code is moved out of the json part to the header part).
            ///////////////////////////////////////
            throw new UpgradeImpossibleException("Upgrade from version 1 to version 2 is impossible because " +
                    "in version 2 the settings file format had to be changed because of unforeseen problems");
        }
    }

    private void throwIfDowngrade(int settingsFileVersion) throws DowngradeNotSupportedException {
        if (settingsFileVersion > CURRENT_EXPORT_SETTINGS_VERSION) {
            // we don't support settings downgrade so just notify the user about it
            throw new DowngradeNotSupportedException(
                    "You are attempting to import settings with version " +
                            "higher than the current app's settings version (downgrade). " +
                            "This is not supported so nothing will be imported. " +
                            "(Current version = " + ImportExportRepository.CURRENT_EXPORT_SETTINGS_VERSION +
                            ", settingFileVersion = " + settingsFileVersion
            );
        }
    }

    private void deleteExportFile(File exportFile) {
        if (exportFile != null) {
            if (!exportFile.delete()) {
                Logger.w(TAG, "Could not delete export file " + exportFile.getAbsolutePath());
            }
        }
    }

    private void writeSettingsToDatabase(
            int settingsFileVersion,
            @NonNull ExportedAppSettings appSettingsParam)
            throws SQLException, IOException {
        ExportedAppSettings appSettings = appSettingsParam;

        if (settingsFileVersion < CURRENT_EXPORT_SETTINGS_VERSION) {
            appSettings = onUpgrade(settingsFileVersion, appSettings);
        }

        databaseHelper.siteDao.deleteBuilder().delete();
        databaseHelper.loadableDao.deleteBuilder().delete();
        databaseHelper.pinDao.deleteBuilder().delete();
        databaseHelper.boardsDao.deleteBuilder().delete();
        databaseHelper.filterDao.deleteBuilder().delete();
        databaseHelper.threadHideDao.deleteBuilder().delete();

        for (ExportedBoard exportedBoard : appSettings.getExportedBoards()) {
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
                    exportedBoard.getCooldownRepliesIntra(),
                    exportedBoard.getCooldownImagesIntra(),
                    exportedBoard.isSpoilers(),
                    exportedBoard.getCustomSpoilers(),
                    exportedBoard.isUserIds(),
                    exportedBoard.isCodeTags(),
                    exportedBoard.isPreuploadCaptcha(),
                    exportedBoard.isCountryFlags(),
                    exportedBoard.isTrollFlags(),
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

            for (ExportedPin exportedPin : exportedSite.getExportedPins()) {
                Loadable loadable = Loadable.importLoadable(
                        inserted.id,
                        exportedPin.getExportedLoadable().getMode(),
                        exportedPin.getExportedLoadable().getBoardCode(),
                        exportedPin.getExportedLoadable().getNo(),
                        exportedPin.getExportedLoadable().getTitle(),
                        exportedPin.getExportedLoadable().getListViewIndex(),
                        exportedPin.getExportedLoadable().getListViewTop(),
                        exportedPin.getExportedLoadable().getLastViewed(),
                        exportedPin.getExportedLoadable().getLastLoaded()
                );
                databaseHelper.loadableDao.createIfNotExists(loadable);

                Pin pin = new Pin(
                        loadable,
                        exportedPin.isWatching(),
                        exportedPin.getWatchLastCount(),
                        exportedPin.getWatchNewCount(),
                        exportedPin.getQuoteLastCount(),
                        exportedPin.getQuoteNewCount(),
                        exportedPin.isError(),
                        exportedPin.getThumbnailUrl(),
                        exportedPin.getOrder(),
                        exportedPin.isArchived()
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
                    exportedFilter.getColor()
            ));
        }

        for (ExportedPostHide exportedPostHide : appSettings.getExportedPostHides()) {
            databaseHelper.threadHideDao.createIfNotExists(new ThreadHide(
                    exportedPostHide.getSite(),
                    exportedPostHide.getBoard(),
                    exportedPostHide.getNo()
            ));
        }

        ChanSettings.deserializeFromString(appSettingsParam.getSettings());
    }

    @NonNull
    private ExportedAppSettings onUpgrade(
            int settingsFileVersion,
            ExportedAppSettings appSettings) {
        // Add your ExportAppSettings migrations here
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
                    pin.loadable.id,
                    pin.order,
                    pin.quoteLastCount,
                    pin.quoteNewCount,
                    pin.thumbnailUrl,
                    pin.watchLastCount,
                    pin.watchNewCount,
                    pin.watching,
                    exportedLoadable
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
                    board.cooldownRepliesIntra,
                    board.cooldownImagesIntra,
                    board.spoilers,
                    board.customSpoilers,
                    board.userIds,
                    board.codeTags,
                    board.preuploadCaptcha,
                    board.countryFlags,
                    board.trollFlags,
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
                    filter.color
            ));
        }

        List<ExportedPostHide> exportedPostHides = new ArrayList<>();

        for (ThreadHide threadHide : databaseHelper.threadHideDao.queryForAll()) {
            exportedPostHides.add(new ExportedPostHide(
                    threadHide.site,
                    threadHide.board,
                    threadHide.no
            ));
        }

        String settings = ChanSettings.serializeToString();

        return new ExportedAppSettings(
                exportedSites,
                exportedBoards,
                exportedFilters,
                exportedPostHides,
                settings
        );
    }

    public interface ImportCallbacks {
        void onSuccessImport();

        void onNothingToImport();

        void onImportError(Throwable error);
    }

    public interface ExportCallbacks {
        void onSuccessExport();

        void onNothingToExport();

        void onExportError(Throwable error);
    }

    public static class DowngradeNotSupportedException extends Exception {
        public DowngradeNotSupportedException(String message) {
            super(message);
        }
    }

    public static class UpgradeImpossibleException extends Exception {
        public UpgradeImpossibleException(String message) {
            super(message);
        }
    }
}
