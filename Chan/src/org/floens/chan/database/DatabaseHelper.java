package org.floens.chan.database;

import java.sql.SQLException;

import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.support.ConnectionSource;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
	private static final String DATABASE_NAME = "ChanDB";
	private static final int DATABASE_VERSION = 1;
	
	public Dao<Pin, Integer> pinDao;
	public Dao<Loadable, Integer> loadableDao;
	
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		
		try {
			pinDao = getDao(Pin.class);
			loadableDao = getDao(Loadable.class);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onCreate(SQLiteDatabase database, ConnectionSource connectionSource) {
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase database, ConnectionSource connectionSource, int oldVersion, int newVersion) {
		
	}
}
