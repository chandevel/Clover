package org.floens.chan.core.repository;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.model.orm.SiteModel;
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
    private static final int CURRENT_EXPORT_SETTINGS_VERSION = 1;

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

    public void exportTo(File cacheDir, ExportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            File exportFile = null;

            try {
                ExportSettings exportSettings = readSettingsFromDatabase();
                if (exportSettings.isEmpty()) {
                    callbacks.onNothingToExport();
                    return null;
                }

                String json = gson.toJson(exportSettings);

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
                callbacks.onExportedSuccessfully();
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to export pins", error);

                deleteExportFile(exportFile);
                callbacks.onError(error);
            }

            return null;
        });
    }

    public void importFrom(File cacheDir, ImportCallbacks callbacks) {
        databaseManager.runTask(() -> {
            try {
                File importFile = new File(cacheDir, EXPORT_FILE_NAME);
                if (!importFile.exists() || !importFile.canRead()) {
                    throw new IOException(
                            "Something wrong with import file (Can't read or it doesn't exist) "
                                    + importFile.getAbsolutePath()
                    );
                }

                ExportSettings exportSettings;

                try (FileReader reader = new FileReader(importFile)) {
                    exportSettings = gson.fromJson(reader, ExportSettings.class);
                }

                writeSettingsToDatabase(exportSettings);

                Logger.d(TAG, "Importing done!");
                callbacks.onImportedSuccessfully();
            } catch (Throwable error) {
                Logger.e(TAG, "Error while trying to import pins", error);
                callbacks.onError(error);
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

    private void writeSettingsToDatabase(@NonNull ExportSettings exportSettings) throws SQLException {
        if (exportSettings.getVersion() < CURRENT_EXPORT_SETTINGS_VERSION) {
            //TODO: do upgrade
        } else if (exportSettings.getVersion() > CURRENT_EXPORT_SETTINGS_VERSION) {
            //TODO: show warning message that downgrade may not work as expected
        }

        databaseHelper.siteDao.deleteBuilder().delete();
        databaseHelper.loadableDao.deleteBuilder().delete();
        databaseHelper.pinDao.deleteBuilder().delete();
        databaseHelper.boardsDao.deleteBuilder().delete();

        for (ExportedBoard exportedBoard : exportSettings.exportedBoards) {
            databaseHelper.boardsDao.createIfNotExists(new Board(
                    exportedBoard.siteId,
                    exportedBoard.saved,
                    exportedBoard.order,
                    exportedBoard.name,
                    exportedBoard.code,
                    exportedBoard.workSafe,
                    exportedBoard.perPage,
                    exportedBoard.pages,
                    exportedBoard.maxFileSize,
                    exportedBoard.maxWebmSize,
                    exportedBoard.maxCommentChars,
                    exportedBoard.bumpLimit,
                    exportedBoard.imageLimit,
                    exportedBoard.cooldownThreads,
                    exportedBoard.cooldownReplies,
                    exportedBoard.cooldownImages,
                    exportedBoard.cooldownRepliesIntra,
                    exportedBoard.cooldownImagesIntra,
                    exportedBoard.spoilers,
                    exportedBoard.customSpoilers,
                    exportedBoard.userIds,
                    exportedBoard.codeTags,
                    exportedBoard.preuploadCaptcha,
                    exportedBoard.countryFlags,
                    exportedBoard.trollFlags,
                    exportedBoard.mathTags,
                    exportedBoard.description,
                    exportedBoard.archive
            ));
        }

        for (ExportedSite exportedSite : exportSettings.exportedSites) {
            SiteModel inserted = databaseHelper.siteDao.createIfNotExists(new SiteModel(
                    exportedSite.getSiteId(),
                    exportedSite.getConfiguration(),
                    exportedSite.getUserSettings(),
                    exportedSite.getOrder()
            ));

            for (ExportedPin exportedPin : exportedSite.exportedPins) {
                Loadable loadable = Loadable.importLoadable(
                        inserted.id,
                        exportedPin.exportedLoadable.mode,
                        exportedPin.exportedLoadable.boardCode,
                        exportedPin.exportedLoadable.no,
                        exportedPin.exportedLoadable.title,
                        exportedPin.exportedLoadable.listViewIndex,
                        exportedPin.exportedLoadable.listViewTop,
                        exportedPin.exportedLoadable.lastViewed,
                        exportedPin.exportedLoadable.lastLoaded
                );
                databaseHelper.loadableDao.createIfNotExists(loadable);

                Pin pin = new Pin(
                        loadable,
                        exportedPin.watching,
                        exportedPin.watchLastCount,
                        exportedPin.watchNewCount,
                        exportedPin.quoteNewCount,
                        exportedPin.quoteLastCount,
                        exportedPin.isError,
                        exportedPin.thumbnailUrl,
                        exportedPin.order,
                        exportedPin.archived
                );
                databaseHelper.pinDao.createIfNotExists(pin);
            }
        }
    }

    @NonNull
    private ExportSettings readSettingsFromDatabase() throws java.sql.SQLException {
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
            return new ExportSettings(new ArrayList<>(), new ArrayList<>());
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

        return new ExportSettings(exportedSites, exportedBoards);
    }

    private static class ExportSettings {
        @SerializedName("version")
        private int version = CURRENT_EXPORT_SETTINGS_VERSION;
        @SerializedName("exported_sites")
        private List<ExportedSite> exportedSites;
        @SerializedName("exported_boards")
        private List<ExportedBoard> exportedBoards;

        public ExportSettings(List<ExportedSite> exportedSites, List<ExportedBoard> exportedBoards) {
            this.exportedSites = exportedSites;
            this.exportedBoards = exportedBoards;
        }

        public boolean isEmpty() {
            return exportedSites.isEmpty() && exportedBoards.isEmpty();
        }

        public List<ExportedSite> getExportedSites() {
            return exportedSites;
        }

        public void setExportedSites(List<ExportedSite> exportedSites) {
            this.exportedSites = exportedSites;
        }

        public List<ExportedBoard> getExportedBoards() {
            return exportedBoards;
        }

        public void setExportedBoards(List<ExportedBoard> exportedBoards) {
            this.exportedBoards = exportedBoards;
        }

        public int getVersion() {
            return version;
        }
    }

    private static class ExportedBoard {
        @SerializedName("site_id")
        private int siteId;
        @SerializedName("saved")
        private boolean saved;
        @SerializedName("order")
        private int order;
        @SerializedName("name")
        private String name;
        @SerializedName("code")
        private String code;
        @SerializedName("work_safe")
        private boolean workSafe;
        @SerializedName("per_page")
        private int perPage;
        @SerializedName("pages")
        private int pages;
        @SerializedName("max_file_size")
        private int maxFileSize;
        @SerializedName("max_webm_size")
        private int maxWebmSize;
        @SerializedName("max_comment_chars")
        private int maxCommentChars;
        @SerializedName("bump_limit")
        private int bumpLimit;
        @SerializedName("image_limit")
        private int imageLimit;
        @SerializedName("cooldown_threads")
        private int cooldownThreads;
        @SerializedName("cooldown_replies")
        private int cooldownReplies;
        @SerializedName("cooldown_images")
        private int cooldownImages;
        @SerializedName("cooldown_replies_intra")
        private int cooldownRepliesIntra;
        @SerializedName("cooldown_images_intra")
        private int cooldownImagesIntra;
        @SerializedName("spoilers")
        private boolean spoilers;
        @SerializedName("custom_spoilers")
        private int customSpoilers;
        @SerializedName("user_ids")
        private boolean userIds;
        @SerializedName("code_tags")
        private boolean codeTags;
        @SerializedName("preupload_captcha")
        private boolean preuploadCaptcha;
        @SerializedName("country_flags")
        private boolean countryFlags;
        @SerializedName("troll_flags")
        private boolean trollFlags;
        @SerializedName("math_tags")
        private boolean mathTags;
        @SerializedName("description")
        private String description;
        @SerializedName("archive")
        private boolean archive;

        public ExportedBoard(
                int siteId,
                boolean saved,
                int order,
                String name,
                String code,
                boolean workSafe,
                int perPage,
                int pages,
                int maxFileSize,
                int maxWebmSize,
                int maxCommentChars,
                int bumpLimit,
                int imageLimit,
                int cooldownThreads,
                int cooldownReplies,
                int cooldownImages,
                int cooldownRepliesIntra,
                int cooldownImagesIntra,
                boolean spoilers,
                int customSpoilers,
                boolean userIds,
                boolean codeTags,
                boolean preuploadCaptcha,
                boolean countryFlags,
                boolean trollFlags,
                boolean mathTags,
                String description,
                boolean archive
        ) {
            this.siteId = siteId;
            this.saved = saved;
            this.order = order;
            this.name = name;
            this.code = code;
            this.workSafe = workSafe;
            this.perPage = perPage;
            this.pages = pages;
            this.maxFileSize = maxFileSize;
            this.maxWebmSize = maxWebmSize;
            this.maxCommentChars = maxCommentChars;
            this.bumpLimit = bumpLimit;
            this.imageLimit = imageLimit;
            this.cooldownThreads = cooldownThreads;
            this.cooldownReplies = cooldownReplies;
            this.cooldownImages = cooldownImages;
            this.cooldownRepliesIntra = cooldownRepliesIntra;
            this.cooldownImagesIntra = cooldownImagesIntra;
            this.spoilers = spoilers;
            this.customSpoilers = customSpoilers;
            this.userIds = userIds;
            this.codeTags = codeTags;
            this.preuploadCaptcha = preuploadCaptcha;
            this.countryFlags = countryFlags;
            this.trollFlags = trollFlags;
            this.mathTags = mathTags;
            this.description = description;
            this.archive = archive;
        }

        public int getSiteId() {
            return siteId;
        }

        public boolean isSaved() {
            return saved;
        }

        public int getOrder() {
            return order;
        }

        public String getName() {
            return name;
        }

        public String getCode() {
            return code;
        }

        public boolean isWorkSafe() {
            return workSafe;
        }

        public int getPerPage() {
            return perPage;
        }

        public int getPages() {
            return pages;
        }

        public int getMaxFileSize() {
            return maxFileSize;
        }

        public int getMaxWebmSize() {
            return maxWebmSize;
        }

        public int getMaxCommentChars() {
            return maxCommentChars;
        }

        public int getBumpLimit() {
            return bumpLimit;
        }

        public int getImageLimit() {
            return imageLimit;
        }

        public int getCooldownThreads() {
            return cooldownThreads;
        }

        public int getCooldownReplies() {
            return cooldownReplies;
        }

        public int getCooldownImages() {
            return cooldownImages;
        }

        public int getCooldownRepliesIntra() {
            return cooldownRepliesIntra;
        }

        public int getCooldownImagesIntra() {
            return cooldownImagesIntra;
        }

        public boolean isSpoilers() {
            return spoilers;
        }

        public int getCustomSpoilers() {
            return customSpoilers;
        }

        public boolean isUserIds() {
            return userIds;
        }

        public boolean isCodeTags() {
            return codeTags;
        }

        public boolean isPreuploadCaptcha() {
            return preuploadCaptcha;
        }

        public boolean isCountryFlags() {
            return countryFlags;
        }

        public boolean isTrollFlags() {
            return trollFlags;
        }

        public boolean isMathTags() {
            return mathTags;
        }

        public String getDescription() {
            return description;
        }

        public boolean isArchive() {
            return archive;
        }
    }

    private static class ExportedSite {
        @SerializedName("site_id")
        private int siteId;
        @SerializedName("configuration")
        private String configuration;
        @SerializedName("order")
        private int order;
        @SerializedName("user_settings")
        private String userSettings;
        @SerializedName("exported_pins")
        private List<ExportedPin> exportedPins;

        public ExportedSite(
                int siteId,
                String configuration,
                int order,
                String userSettings,
                List<ExportedPin> exportedPins
        ) {
            this.siteId = siteId;
            this.configuration = configuration;
            this.order = order;
            this.userSettings = userSettings;
            this.exportedPins = exportedPins;
        }

        public int getSiteId() {
            return siteId;
        }

        public String getConfiguration() {
            return configuration;
        }

        public int getOrder() {
            return order;
        }

        public String getUserSettings() {
            return userSettings;
        }

        public List<ExportedPin> getExportedPins() {
            return exportedPins;
        }
    }

    private static class ExportedPin {
        @SerializedName("archived")
        private boolean archived;
        @SerializedName("pin_id")
        private int pinId;
        @SerializedName("is_error")
        private boolean isError;
        @SerializedName("loadable_id")
        private int loadableId;
        @SerializedName("order")
        private int order;
        @SerializedName("quote_last_count")
        private int quoteLastCount;
        @SerializedName("quote_new_count")
        private int quoteNewCount;
        @SerializedName("thumbnail_url")
        private String thumbnailUrl;
        @SerializedName("watch_last_count")
        private int watchLastCount;
        @SerializedName("watch_new_count")
        private int watchNewCount;
        @SerializedName("watching")
        private boolean watching;
        @SerializedName("exported_loadable")
        private ExportedLoadable exportedLoadable;

        public ExportedPin(
                boolean archived,
                int pinId,
                boolean isError,
                int loadableId,
                int order,
                int quoteLastCount,
                int quoteNewCount,
                String thumbnailUrl,
                int watchLastCount,
                int watchNewCount,
                boolean watching,
                ExportedLoadable exportedLoadable
        ) {
            this.archived = archived;
            this.pinId = pinId;
            this.isError = isError;
            this.loadableId = loadableId;
            this.order = order;
            this.quoteLastCount = quoteLastCount;
            this.quoteNewCount = quoteNewCount;
            this.thumbnailUrl = thumbnailUrl;
            this.watchLastCount = watchLastCount;
            this.watchNewCount = watchNewCount;
            this.watching = watching;
            this.exportedLoadable = exportedLoadable;
        }
    }

    private static class ExportedLoadable {
        @SerializedName("board_code")
        private String boardCode;
        @SerializedName("loadable_id")
        private long loadableId;
        @SerializedName("last_loaded")
        private int lastLoaded;
        @SerializedName("last_viewed")
        private int lastViewed;
        @SerializedName("list_view_index")
        private int listViewIndex;
        @SerializedName("list_view_top")
        private int listViewTop;
        @SerializedName("mode")
        private int mode;
        @SerializedName("no")
        private int no;
        @SerializedName("site_id")
        private int siteId;
        @SerializedName("title")
        private String title;

        public ExportedLoadable(
                String boardCode,
                long loadableId,
                int lastLoaded,
                int lastViewed,
                int listViewIndex,
                int listViewTop,
                int mode,
                int no,
                int siteId,
                String title
        ) {
            this.boardCode = boardCode;
            this.loadableId = loadableId;
            this.lastLoaded = lastLoaded;
            this.lastViewed = lastViewed;
            this.listViewIndex = listViewIndex;
            this.listViewTop = listViewTop;
            this.mode = mode;
            this.no = no;
            this.siteId = siteId;
            this.title = title;
        }
    }

    public interface ExportCallbacks {
        void onExportedSuccessfully();

        void onNothingToExport();

        void onError(Throwable error);
    }

    public interface ImportCallbacks {
        void onImportedSuccessfully();

        void onError(Throwable error);
    }
}
