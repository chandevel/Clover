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
package com.github.adamantcheese.chan.core.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.History;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.settings.primitives.BooleanSetting;
import com.github.adamantcheese.chan.core.settings.primitives.IntegerSetting;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.settings.primitives.LongSetting;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting;
import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;
import com.github.adamantcheese.chan.core.settings.provider.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getPreferences;

public class DatabaseHelper
        extends OrmLiteSqliteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "ChanDB";
    private static final int DATABASE_VERSION = 46;

    public Dao<Pin, Integer> pinDao;
    public Dao<Loadable, Integer> loadableDao;
    public Dao<SavedReply, Integer> savedDao;
    public Dao<Board, Integer> boardsDao;
    public Dao<PostHide, Integer> postHideDao;
    public Dao<History, Integer> historyDao;
    public Dao<Filter, Integer> filterDao;
    public Dao<SiteModel, Integer> siteDao;
    public Dao<SavedThread, Integer> savedThreadDao;

    private final Context context;

    @Inject
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        this.context = context;

        try {
            pinDao = getDao(Pin.class);
            loadableDao = getDao(Loadable.class);
            savedDao = getDao(SavedReply.class);
            boardsDao = getDao(Board.class);
            postHideDao = getDao(PostHide.class);
            historyDao = getDao(History.class);
            filterDao = getDao(Filter.class);
            siteDao = getDao(SiteModel.class);
            savedThreadDao = getDao(SavedThread.class);
        } catch (SQLException e) {
            Logger.e(this, "Error creating dao's", e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            createTables(connectionSource);
        } catch (SQLException e) {
            Logger.e(this, "Error creating db", e);
            throw new RuntimeException(e);
        }
    }

    public void createTables(ConnectionSource connectionSource)
            throws SQLException {
        TableUtils.createTable(connectionSource, Pin.class);
        TableUtils.createTable(connectionSource, Loadable.class);
        TableUtils.createTable(connectionSource, SavedReply.class);
        TableUtils.createTable(connectionSource, Board.class);
        TableUtils.createTable(connectionSource, PostHide.class);
        TableUtils.createTable(connectionSource, History.class);
        TableUtils.createTable(connectionSource, Filter.class);
        TableUtils.createTable(connectionSource, SiteModel.class);
        TableUtils.createTable(connectionSource, SavedThread.class);
    }

    public void dropTables(ConnectionSource connectionSource)
            throws SQLException {
        TableUtils.dropTable(connectionSource, Pin.class, true);
        TableUtils.dropTable(connectionSource, Loadable.class, true);
        TableUtils.dropTable(connectionSource, SavedReply.class, true);
        TableUtils.dropTable(connectionSource, Board.class, true);
        TableUtils.dropTable(connectionSource, PostHide.class, true);
        TableUtils.dropTable(connectionSource, History.class, true);
        TableUtils.dropTable(connectionSource, Filter.class, true);
        TableUtils.dropTable(connectionSource, SiteModel.class, true);
        TableUtils.dropTable(connectionSource, SavedThread.class, true);
    }

    /**
     * When modifying the database columns do no forget to change the {@link com.github.adamantcheese.chan.core.model.export.ExportedAppSettings} as well
     * and add your handler in {@link com.github.adamantcheese.chan.core.repository.ImportExportRepository} onUpgrade method
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        Logger.i(this, "Upgrading database from " + oldVersion + " to " + newVersion);

        if (oldVersion < 25) {
            try {
                boardsDao.executeRawNoArgs("CREATE INDEX board_site_idx ON board(site);");
                boardsDao.executeRawNoArgs("CREATE INDEX board_saved_idx ON board(saved);");
                boardsDao.executeRawNoArgs("CREATE INDEX board_value_idx ON board(value);");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 25", e);
            }
        }

        if (oldVersion < 26) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE threadhide RENAME TO posthide;");
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN whole_thread INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 26", e);
            }
        }

        if (oldVersion < 27) {
            try {
                // Create indexes for PostHides to speed up posts filtering
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_site_idx ON posthide(site);");
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_board_idx ON posthide(board);");
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_no_idx ON posthide(no);");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 27", e);
            }
        }

        if (oldVersion < 28) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN hide INTEGER default 0");
                postHideDao.executeRawNoArgs(
                        "ALTER TABLE posthide ADD COLUMN hide_replies_to_this_post INTEGER default 0");
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN apply_to_replies INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 28", e);
            }
        }

        if (oldVersion < 29) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN thread_no INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 29", e);
            }
        }

        if (oldVersion < 30) {
            try {
                boardsDao.executeRawNoArgs("BEGIN TRANSACTION;\n"
                        + "CREATE TEMPORARY TABLE board_backup(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);\n"
                        + "INSERT INTO board_backup SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board;\n"
                        + "DROP TABLE board;\n"
                        + "CREATE TABLE board(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);\n"
                        + "INSERT INTO board SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board_backup;\n"
                        + "DROP TABLE board_backup;\n" + "COMMIT;");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 30");
            }
        }

        if (oldVersion < 31) {
            try {
                loadableDao.executeRawNoArgs("UPDATE loadable SET mode = 1 WHERE mode = 2");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 31");
            }
        }

        if (oldVersion < 32) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN \"order\" INTEGER");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 32");
            }
        }

        //33 set the new captcha window to default, but it's always on now so this was removed

        if (oldVersion < 34) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN onlyOnOP INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 34");
            }
        }

        if (oldVersion < 35) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN applyToSaved INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 35");
            }
        }

        if (oldVersion < 36) {
            try {
                filterDao.executeRawNoArgs(
                        "CREATE TABLE `saved_thread` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, `loadable_id` INTEGER NOT NULL , `last_saved_post_no` INTEGER NOT NULL DEFAULT 0, `is_fully_downloaded` INTEGER NOT NULL DEFAULT 0 , `is_stopped` INTEGER NOT NULL DEFAULT 0);");
                filterDao.executeRawNoArgs("CREATE INDEX loadable_id_idx ON saved_thread(loadable_id);");

                // Because pins now has different type (the ones that watch threads and ones that
                // download them (also they can do both at the same time)) we need to use DEFAULT 1
                // to set flag WatchNewPosts for all of the old pins
                filterDao.executeRawNoArgs("ALTER TABLE pin ADD COLUMN pin_type INTEGER NOT NULL DEFAULT 1");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 36", e);
            }
        }

        if (oldVersion < 37) {
            try {
                //reset all settings, key was never saved which caused issues
                siteDao.executeRawNoArgs("UPDATE site SET userSettings='{}'");
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 37", e);
            }
        }

        if (oldVersion < 38) {
            try {
                Logger.d(this, "Removing Chan55");
                deleteSiteByRegistryID(7);
                Logger.d(this, "Removed Chan55 successfully");
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 38");
            }
        }

        if (oldVersion < 39) {
            try {
                Logger.d(this, "Removing 8Chan");
                deleteSiteByRegistryID(1);
                Logger.d(this, "Removed 8Chan successfully");
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 39");
            }
        }

        if (oldVersion < 40) {
            try {
                //disable Youtube link parsing if it was enabled in a previous version to prevent issues
                ChanSettings.parseYoutubeTitles.set(false);

                //remove arisuchan boards that don't exist anymore
                Where<Board, Integer> where = boardsDao.queryBuilder().where();
                where.and(where.eq("site", 3), where.or(
                        where.eq("value", "cyb"),
                        where.eq("value", "feels"),
                        where.eq("value", "x"),
                        where.eq("value", "z")
                ));
                List<Board> toRemove = where.query();
                for (Board b : toRemove) {
                    if (b != null) {
                        deleteBoard(b);
                    }
                }

                //some descriptions changed for arisuchan
                try {
                    Board art = boardsDao.queryForEq("key", "art and design").get(0);
                    if (art != null) {
                        art.name = "art and creative";
                        boardsDao.update(art);
                    }
                } catch (Exception ignored) {
                }
                try {
                    Board sci = boardsDao.queryForEq("key", "science and technology").get(0);
                    if (sci != null) {
                        sci.name = "technology";
                        boardsDao.update(sci);
                    }
                } catch (Exception ignored) {
                }
                try {
                    Board diy = boardsDao.queryForEq("key", "diy and projects").get(0);
                    if (diy != null) {
                        diy.name = "shape your world";
                        boardsDao.update(diy);
                    }
                } catch (Exception ignored) {
                }
                try {
                    Board ru = boardsDao.queryForEq("key", "киберпанк-доска").get(0);
                    if (ru != null) {
                        ru.name = "Киберпанк";
                        boardsDao.update(ru);
                    }
                } catch (Exception ignored) {
                }
            } catch (SQLException e) {
                Logger.e(this, "Error upgrading to version 40");
            }
        }

        if (oldVersion < 41) {
            //enable the following as default for 4.10.2
            ChanSettings.parsePostImageLinks.set(true);
            ChanSettings.parseYoutubeTitles.set(true);
        }

        if (oldVersion < 42) {
            try {
                //remove wired-7 boards that don't exist anymore
                Where<Board, Integer> where = boardsDao.queryBuilder().where();
                where.and(where.eq("site", 6), where.eq("value", "18"));
                List<Board> toRemove = where.query();
                for (Board b : toRemove) {
                    if (b != null) {
                        deleteBoard(b);
                    }
                }
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 42");
            }
        }

        if (oldVersion < 43) {
            try {
                Logger.d(this, "Removing Arisuchan");
                deleteSiteByRegistryID(3);
                Logger.d(this, "Removed Arisuchan successfully");
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 43");
            }
        }

        if (oldVersion < 44) {
            try {
                SettingProvider<Object> p = new SharedPreferencesSettingProvider(getPreferences());
                // the PersistableChanState class was added, so some settings are being moved over there
                // get settings from before the move
                IntegerSetting watchLastCount =
                        getSettingForKey(p, "preference_watch_last_count", IntegerSetting.class, Integer.class);
                IntegerSetting previousVersion =
                        getSettingForKey(p, "preference_previous_version", IntegerSetting.class, Integer.class);
                LongSetting updateCheckTime = getSettingForKey(p, "update_check_time", LongSetting.class, Long.class);
                StringSetting previousDevHash =
                        getSettingForKey(p, "previous_dev_hash", StringSetting.class, String.class);
                StringSetting filterWatchIgnores =
                        getSettingForKey(p, "filter_watch_last_ignored_set", StringSetting.class, String.class);
                StringSetting youtubeTitleCache =
                        getSettingForKey(p, "yt_title_cache", StringSetting.class, String.class);
                StringSetting youtubeDurCache = getSettingForKey(p, "yt_dur_cache", StringSetting.class, String.class);

                // update a few of them
                PersistableChanState.watchLastCount.setSync(watchLastCount.get());
                PersistableChanState.previousVersion.setSync(previousVersion.get());
                PersistableChanState.updateCheckTime.setSync(updateCheckTime.get());
                PersistableChanState.previousDevHash.setSync(previousDevHash.get());

                // remove them; they are now in PersistableChanState
                p.removeSync(watchLastCount.getKey());
                p.removeSync(previousVersion.getKey());
                p.removeSync(updateCheckTime.getKey());
                p.removeSync(previousDevHash.getKey());
                p.removeSync(filterWatchIgnores.getKey());
                p.removeSync(youtubeTitleCache.getKey());
                p.removeSync(youtubeDurCache.getKey());

                // Preference key changed, move it over
                BooleanSetting uploadCrashLogs =
                        getSettingForKey(p, "auto_upload_crash_logs", BooleanSetting.class, Boolean.class);
                ChanSettings.collectCrashLogs.set(uploadCrashLogs.get());
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 44");
            }
        }

        if (oldVersion < 45) {
            try {
                loadableDao.executeRawNoArgs(
                        "ALTER TABLE loadable ADD COLUMN lastLoadDate TIMESTAMP default '1970-01-01 00:00:01'");
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                String currentTime = format.format(GregorianCalendar.getInstance().getTime());
                loadableDao.executeRawNoArgs("UPDATE loadable SET lastLoadDate='" + currentTime + "'");
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 45");
            }
        }

        if (oldVersion < 46) {
            try {
                //sqlite doesn't have a proper modify command to remove the NOT NULL constraint from thumbnailUrl
                pinDao.executeRawNoArgs("ALTER TABLE pin RENAME TO pin_old"); // rename existing table
                TableUtils.createTable(connectionSource, Pin.class); // recreate table
                pinDao.executeRawNoArgs("INSERT INTO pin SELECT * FROM pin_old"); // copy table
                pinDao.executeRawNoArgs("DROP TABLE pin_old"); // delete old table
            } catch (Exception e) {
                Logger.e(this, "Error upgrading to version 46");
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.enableWriteAheadLogging();
    }

    public void reset() {
        Logger.i(this, "Resetting database!");

        if (context.deleteDatabase(DATABASE_NAME)) {
            Logger.i(this, "Deleted database");
        }
    }

    /**
     * This method deletes a site by the id it was given in SiteRegistry, but in the database rather than in the application
     * This is useful for when a site's classes have been removed from the application
     *
     * @param id The ID given in SiteRegistry before this site's class was removed
     */
    public void deleteSiteByRegistryID(int id)
            throws SQLException {
        //NOTE: most of this is a copy of the methods used by the runtime version, but condensed down into one method
        //convert the SiteRegistry id to the actual database id
        List<SiteModel> allSites = siteDao.queryForAll();
        SiteModel toDelete = null;
        for (SiteModel siteModel : allSites) {
            Pair<SiteConfig, JsonSettings> siteModelConfig = siteModel.loadConfigFields();
            if (siteModelConfig.first.classId == id) {
                toDelete = siteModel;
                break;
            }
        }
        //if we can't find it then it doesn't exist so we don't need to delete anything
        if (toDelete == null) return;

        //filters
        List<Filter> filtersToDelete = new ArrayList<>();

        for (Filter filter : filterDao.queryForAll()) {
            if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
                continue;
            }

            for (String uniqueId : filter.boards.split(",")) {
                String[] split = uniqueId.split(":");
                if (split.length == 2 && Integer.parseInt(split[0]) == toDelete.id) {
                    filtersToDelete.add(filter);
                    break;
                }
            }
        }

        Set<Integer> filterIdSet = new HashSet<>();
        for (Filter filter : filtersToDelete) {
            filterIdSet.add(filter.id);
        }

        DeleteBuilder<Filter, Integer> filterDelete = filterDao.deleteBuilder();
        filterDelete.where().in("id", filterIdSet);

        int deletedCountFilters = filterDelete.delete();
        if (deletedCountFilters != filterIdSet.size()) {
            throw new IllegalStateException(
                    "Deleted count didn't equal filterIdList.size(). (deletedCount = " + deletedCountFilters + "), "
                            + "(filterIdSet = " + filterIdSet.size() + ")");
        }

        //boards
        DeleteBuilder<Board, Integer> boardDelete = boardsDao.deleteBuilder();
        boardDelete.where().eq("site", toDelete.id);
        boardDelete.delete();

        //loadables (saved threads, pins, history, loadables)
        List<Loadable> siteLoadables = loadableDao.queryForEq("site", toDelete.id);
        if (!siteLoadables.isEmpty()) {
            Set<Integer> loadableIdSet = new HashSet<>();
            for (Loadable loadable : siteLoadables) {
                loadableIdSet.add(loadable.id);
            }
            //saved threads
            DeleteBuilder<SavedThread, Integer> savedThreadDelete = savedThreadDao.deleteBuilder();
            savedThreadDelete.where().in("loadable_id", loadableIdSet);
            savedThreadDelete.delete();

            //pins
            DeleteBuilder<Pin, Integer> pinDelete = pinDao.deleteBuilder();
            pinDelete.where().in("loadable_id", loadableIdSet);
            pinDelete.delete();

            //history
            DeleteBuilder<History, Integer> historyDelete = historyDao.deleteBuilder();
            historyDelete.where().in("loadable_id", loadableIdSet);
            historyDelete.delete();

            //loadables
            DeleteBuilder<Loadable, Integer> loadableDelete = loadableDao.deleteBuilder();
            loadableDelete.where().in("id", loadableIdSet);

            int deletedCountLoadables = loadableDelete.delete();
            if (loadableIdSet.size() != deletedCountLoadables) {
                throw new IllegalStateException(
                        "Deleted count didn't equal loadableIdSet.size(). (deletedCount = " + deletedCountLoadables
                                + "), (loadableIdSet = " + loadableIdSet.size() + ")");
            }
        }

        //saved replies
        DeleteBuilder<SavedReply, Integer> savedReplyDelete = savedDao.deleteBuilder();
        savedReplyDelete.where().eq("site", toDelete.id);
        savedReplyDelete.delete();

        //thread hides
        DeleteBuilder<PostHide, Integer> threadHideDelete = postHideDao.deleteBuilder();
        threadHideDelete.where().eq("site", toDelete.id);
        threadHideDelete.delete();

        //site itself
        DeleteBuilder<SiteModel, Integer> siteDelete = siteDao.deleteBuilder();
        siteDelete.where().eq("id", toDelete.id);
        siteDelete.delete();
    }

    /**
     * Deletes a board on a given site when the site no longer supports the given board
     */
    public void deleteBoard(Board board)
            throws SQLException {
        //filters
        for (Filter filter : filterDao.queryForAll()) {
            if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
                continue;
            }

            List<String> keep = new ArrayList<>();
            for (String uniqueId : filter.boards.split(",")) {
                String[] split = uniqueId.split(":");
                if (!(split.length == 2 && Integer.parseInt(split[0]) == board.siteId && split[1].equals(board.code))) {
                    keep.add(uniqueId);
                }
            }
            filter.boards = TextUtils.join(",", keep);
            //disable, but don't delete filters in case they're still wanted
            if (TextUtils.isEmpty(filter.boards)) {
                filter.enabled = false;
            }

            filterDao.update(filter);
        }

        //loadables (saved threads, pins, history, loadables)
        List<Loadable> siteLoadables = loadableDao.queryForEq("site", board.siteId);
        if (!siteLoadables.isEmpty()) {
            Set<Integer> loadableIdSet = new HashSet<>();
            for (Loadable loadable : siteLoadables) {
                //only get the loadables with the same board code
                if (loadable.boardCode.equals(board.code)) {
                    loadableIdSet.add(loadable.id);
                }
            }
            //saved threads
            DeleteBuilder<SavedThread, Integer> savedThreadDelete = savedThreadDao.deleteBuilder();
            savedThreadDelete.where().in("loadable_id", loadableIdSet);
            savedThreadDelete.delete();

            //pins
            DeleteBuilder<Pin, Integer> pinDelete = pinDao.deleteBuilder();
            pinDelete.where().in("loadable_id", loadableIdSet);
            pinDelete.delete();

            //history
            DeleteBuilder<History, Integer> historyDelete = historyDao.deleteBuilder();
            historyDelete.where().in("loadable_id", loadableIdSet);
            historyDelete.delete();

            //loadables
            DeleteBuilder<Loadable, Integer> loadableDelete = loadableDao.deleteBuilder();
            loadableDelete.where().in("id", loadableIdSet);

            int deletedCountLoadables = loadableDelete.delete();
            if (loadableIdSet.size() != deletedCountLoadables) {
                throw new IllegalStateException(
                        "Deleted count didn't equal loadableIdSet.size(). (deletedCount = " + deletedCountLoadables
                                + "), (loadableIdSet = " + loadableIdSet.size() + ")");
            }
        }

        //saved replies
        DeleteBuilder<SavedReply, Integer> savedReplyDelete = savedDao.deleteBuilder();
        savedReplyDelete.where().eq("site", board.siteId).and().eq("board", board.code);
        savedReplyDelete.delete();

        //thread hides
        DeleteBuilder<PostHide, Integer> threadHideDelete = postHideDao.deleteBuilder();
        threadHideDelete.where().eq("site", board.siteId).and().eq("board", board.code);
        threadHideDelete.delete();

        //board itself
        DeleteBuilder<Board, Integer> boardDelete = boardsDao.deleteBuilder();
        boardDelete.where().eq("site", board.siteId).and().eq("value", board.code);
        boardDelete.delete();
    }

    /**
     * @param p    the provider to get the setting from
     * @param key  the key for the setting
     * @param type the class of the setting, see parameter T; pass in Setting.class for whatever setting class you need
     * @param <T>  the type of the setting, should extend Setting
     * @return the setting requested, or null
     */
    public static <T, S> T getSettingForKey(SettingProvider<Object> p, String key, Class<T> type, Class<S> def) {
        if (!Setting.class.isAssignableFrom(type)) return null;
        try {
            Constructor<T> c = type.getConstructor(SettingProvider.class, String.class, def);
            c.setAccessible(true);
            Object defArg = null;
            if (Integer.class.equals(def)) {
                defArg = 0;
            } else if (Long.class.equals(def)) {
                defArg = 0L;
            } else if (String.class.equals(def)) {
                defArg = "";
            } else if (Boolean.class.equals(def)) {
                defArg = Boolean.FALSE;
            }
            T returnSetting = c.newInstance(p, key, defArg);
            c.setAccessible(false);
            return returnSetting;
        } catch (Exception failedSomething) {
            Logger.e(TAG, "Reflection failed", failedSomething);
            return null;
        }
    }
}
