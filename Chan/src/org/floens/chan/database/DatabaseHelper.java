package org.floens.chan.database;

import java.sql.SQLException;

import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.utils.Logger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final String TAG = "DatabaseHelper";

    private static final String DATABASE_NAME = "ChanDB";
    private static final int DATABASE_VERSION = 9;

    public Dao<Pin, Integer> pinDao;
    public Dao<Loadable, Integer> loadableDao;
    public Dao<SavedReply, Integer> savedDao;

    private final Context context;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);

        this.context = context;

        try {
            pinDao = getDao(Pin.class);
            loadableDao = getDao(Loadable.class);
            savedDao = getDao(SavedReply.class);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, Pin.class);
            TableUtils.createTable(connectionSource, Loadable.class);
            TableUtils.createTable(connectionSource, SavedReply.class);
        } catch (SQLException e) {
            Logger.e(TAG, "Error creating db", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        switch (oldVersion) {
        // Change tables if we make adjustments
        }

        // Drop the tables and recreate them for now
        reset(database, connectionSource);
    }

    public void reset() {
        Logger.i(TAG, "Resetting database!");

        if (context.deleteDatabase(DATABASE_NAME)) {
            Logger.i(TAG, "Deleted database");
        }
    }

    private void reset(SQLiteDatabase database, ConnectionSource connectionSource) {
        try {
            TableUtils.dropTable(connectionSource, Pin.class, true);
            TableUtils.dropTable(connectionSource, Loadable.class, true);
            TableUtils.dropTable(connectionSource, SavedReply.class, true);

            onCreate(database, connectionSource);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
