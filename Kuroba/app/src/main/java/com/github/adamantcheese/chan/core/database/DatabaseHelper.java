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

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.History;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.model.orm.SavedReply;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

import javax.inject.Inject;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "ChanDB";
    private static final int DATABASE_VERSION = 35;

    public Dao<Pin, Integer> pinDao;
    public Dao<Loadable, Integer> loadableDao;
    public Dao<SavedReply, Integer> savedDao;
    public Dao<Board, Integer> boardsDao;
    public Dao<PostHide, Integer> postHideDao;
    public Dao<History, Integer> historyDao;
    public Dao<Filter, Integer> filterDao;
    public Dao<SiteModel, Integer> siteDao;

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
        } catch (SQLException e) {
            Logger.e(TAG, "Error creating dao's", e);
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Pin.class);
            TableUtils.createTable(connectionSource, Loadable.class);
            TableUtils.createTable(connectionSource, SavedReply.class);
            TableUtils.createTable(connectionSource, Board.class);
            TableUtils.createTable(connectionSource, PostHide.class);
            TableUtils.createTable(connectionSource, History.class);
            TableUtils.createTable(connectionSource, Filter.class);
            TableUtils.createTable(connectionSource, SiteModel.class);
        } catch (SQLException e) {
            Logger.e(TAG, "Error creating db", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * When modifying the database columns do no forget to change the {@link com.github.adamantcheese.chan.core.model.export.ExportedAppSettings} as well
     * and add your handler in {@link com.github.adamantcheese.chan.core.repository.ImportExportRepository} onUpgrade method
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        Logger.i(TAG, "Upgrading database from " + oldVersion + " to " + newVersion);

        if (oldVersion < 25) {
            try {
                boardsDao.executeRawNoArgs("CREATE INDEX board_site_idx ON board(site);");
                boardsDao.executeRawNoArgs("CREATE INDEX board_saved_idx ON board(saved);");
                boardsDao.executeRawNoArgs("CREATE INDEX board_value_idx ON board(value);");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 25", e);
            }
        }

        if (oldVersion < 26) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE threadhide RENAME TO posthide;");
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN whole_thread INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 26", e);
            }
        }

        if (oldVersion < 27) {
            try {
                // Create indexes for PostHides to speed up posts filtering
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_site_idx ON posthide(site);");
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_board_idx ON posthide(board);");
                postHideDao.executeRawNoArgs("CREATE INDEX posthide_no_idx ON posthide(no);");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 27", e);
            }
        }

        if (oldVersion < 28) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN hide INTEGER default 0");
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN hide_replies_to_this_post INTEGER default 0");
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN apply_to_replies INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 28", e);
            }
        }

        if (oldVersion < 29) {
            try {
                postHideDao.executeRawNoArgs("ALTER TABLE posthide ADD COLUMN thread_no INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 29", e);
            }
        }

        if (oldVersion < 30) {
            try {
                boardsDao.executeRawNoArgs("BEGIN TRANSACTION;\n" +
                        "CREATE TEMPORARY TABLE board_backup(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);\n" +
                        "INSERT INTO board_backup SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board;\n" +
                        "DROP TABLE board;\n" +
                        "CREATE TABLE board(archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe);\n" +
                        "INSERT INTO board SELECT archive,bumplimit,value,codeTags,cooldownImages,cooldownReplies,cooldownThreads,countryFlags,customSpoilers,description,id,imageLimit,mathTags,maxCommentChars,maxFileSize,maxWebmSize,key,order,pages,perPage,preuploadCaptcha,saved,site,spoilers,userIds,workSafe FROM board_backup;\n" +
                        "DROP TABLE board_backup;\n" +
                        "COMMIT;");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 30");
            }
        }

        if (oldVersion < 31) {
            try {
                loadableDao.executeRawNoArgs("UPDATE loadable SET mode = 1 WHERE mode = 2");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 31");
            }
        }

        if (oldVersion < 32) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN \"order\" INTEGER");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 32");
            }
        }

        if (oldVersion < 33) {
            //even though this isn't a database thing, it's an easy way of doing things only once
            ChanSettings.useNewCaptchaWindow.set(true);
        }

        if (oldVersion < 34) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN onlyOnOP INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 34");
            }
        }

        if (oldVersion < 35) {
            try {
                filterDao.executeRawNoArgs("ALTER TABLE filter ADD COLUMN applyToSaved INTEGER default 0");
            } catch (SQLException e) {
                Logger.e(TAG, "Error upgrading to version 35");
            }
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.enableWriteAheadLogging();
    }

    public void reset() {
        Logger.i(TAG, "Resetting database!");

        if (context.deleteDatabase(DATABASE_NAME)) {
            Logger.i(TAG, "Deleted database");
        }
    }
}
