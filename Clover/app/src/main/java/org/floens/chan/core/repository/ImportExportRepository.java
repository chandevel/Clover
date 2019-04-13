package org.floens.chan.core.repository;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.google.gson.Gson;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.export.ExportedAppSettings;
import org.floens.chan.core.model.export.ExportedBoard;
import org.floens.chan.core.model.export.ExportedLoadable;
import org.floens.chan.core.model.export.ExportedPin;
import org.floens.chan.core.model.export.ExportedSite;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.model.orm.SiteModel;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
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
    private static final String EXPORT_FILE_NAME = "exported_pins.json";
    public static final int CURRENT_EXPORT_SETTINGS_VERSION = 1;

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

    public void exportTo(File cacheDir, ImportExportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            File exportFile = null;

            try {
                ExportedAppSettings appSettings = readSettingsFromDatabase();
                if (appSettings.isEmpty()) {
                    callbacks.onNothingToImportExport(ImportExport.Export);
                    return null;
                }

                String json = gson.toJson(appSettings);

                exportFile = new File(cacheDir, EXPORT_FILE_NAME);
                if (exportFile.exists()) {
                    if (!exportFile.delete()) {
                        Logger.w(TAG, "Could not delete export file before exporting " + exportFile.getAbsolutePath());
                    }
                }

                if (!exportFile.createNewFile()) {
                    throw new IOException(
                            "Could not create a file for exporting " + exportFile.getAbsolutePath()
                    );
                }

                if (!exportFile.exists() || !exportFile.canWrite()) {
                    throw new IOException(
                            "Something wrong with export file (Can't write or it doesn't exist) "
                                    + exportFile.getAbsolutePath()
                    );
                }

                try (RandomAccessFile raf = new RandomAccessFile(exportFile, "rw")) {
                    raf.writeBytes(json);
                }

                Logger.d(TAG, "Exporting done!");
                callbacks.onSuccess(ImportExport.Export);
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to export pins", error);

                deleteExportFile(exportFile);
                callbacks.onError(error, ImportExport.Export);
            }

            return null;
        });
    }

    public void importFrom(File cacheDir, ImportExportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            try {
                File importFile = new File(cacheDir, EXPORT_FILE_NAME);
                if (!importFile.exists() || !importFile.canRead()) {
                    throw new IOException(
                            "Something wrong with import file (Can't read or it doesn't exist) "
                                    + importFile.getAbsolutePath()
                    );
                }

                ExportedAppSettings appSettings;

                try (FileReader reader = new FileReader(importFile)) {
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
                Logger.e(TAG, "Error while trying to import pins", error);
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

    private void writeSettingsToDatabase(@NonNull ExportedAppSettings appSettingsParam) throws SQLException {
        ExportedAppSettings appSettings = appSettingsParam;

        if (appSettings.getVersion() < CURRENT_EXPORT_SETTINGS_VERSION) {
            appSettings = onUpgrade(appSettings.getVersion(), appSettings);
        } else if (appSettings.getVersion() > CURRENT_EXPORT_SETTINGS_VERSION) {
            throw new IllegalStateException(
                    "Cannot import settings with version higher than current (downgrade)! " +
                            "(Settings version = " + appSettings.getVersion() + ", current version = "
                            + CURRENT_EXPORT_SETTINGS_VERSION + ")"
            );
        }

        databaseHelper.siteDao.deleteBuilder().delete();
        databaseHelper.loadableDao.deleteBuilder().delete();
        databaseHelper.pinDao.deleteBuilder().delete();
        databaseHelper.boardsDao.deleteBuilder().delete();

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

        ChanSettings.deserializeFromString(appSettingsParam.getSettings());
    }

    private ExportedAppSettings onUpgrade(int version, ExportedAppSettings appSettings) {
        // Transform ExportedAppSettings here if necessary
        return appSettings;
    }

    //TODO: filters, hides
    @NonNull
    private ExportedAppSettings readSettingsFromDatabase() throws java.sql.SQLException {
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

        if (exportedSites.isEmpty()) {
            return new ExportedAppSettings(new ArrayList<>(), new ArrayList<>(), "");
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

        String settings = ChanSettings.serializeToString();
        return new ExportedAppSettings(exportedSites, exportedBoards, settings);
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
}
