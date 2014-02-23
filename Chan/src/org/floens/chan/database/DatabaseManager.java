package org.floens.chan.database;

import java.sql.SQLException;
import java.util.List;

import org.floens.chan.model.Pin;

import android.content.Context;

public class DatabaseManager {
	private final DatabaseHelper helper;
	
	public DatabaseManager(Context context) {
		helper = new DatabaseHelper(context);
	}
	
	public void addPin(Pin pin) {
		try {
			helper.pinDao.create(pin);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void removePin(Pin pin) {
		try {
			helper.pinDao.delete(pin);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void updatePin(Pin pin) {
		try {
			helper.pinDao.update(pin);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public List<Pin> getPinned() {
		List<Pin> list = null;
		try {
			 list = helper.pinDao.queryForAll();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		return list;
	}
}
