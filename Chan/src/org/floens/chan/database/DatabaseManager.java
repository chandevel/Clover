package org.floens.chan.database;

import java.sql.SQLException;
import java.util.List;

import org.floens.chan.model.Pin;
import org.floens.chan.utils.Logger;

import android.content.Context;

public class DatabaseManager {
    private static DatabaseManager instance;
    
    private final DatabaseHelper helper;
    
    public DatabaseManager(Context context) {
        instance = this;
        
        helper = new DatabaseHelper(context);
    }
    
    public static DatabaseManager getInstance() {
        return instance;
    }
    
    public void addPin(Pin pin) {
        try {
            helper.loadableDao.create(pin.loadable);
            helper.pinDao.create(pin);
        } catch (SQLException e) {
            Logger.e("Error adding pin to db", e);
        }
    }
    
    public void removePin(Pin pin) {
        try {
            helper.pinDao.delete(pin);
            helper.loadableDao.delete(pin.loadable);
        } catch (SQLException e) {
            Logger.e("Error removing pin from db", e);
        }
    }
    
    public void updatePin(Pin pin) {
        try {
            helper.pinDao.update(pin);
            helper.loadableDao.update(pin.loadable);
        } catch (SQLException e) {
            Logger.e("Error updating pin in db", e);
        }
    }
    
    public void updatePins(List<Pin> pins) {
        try {
            for (Pin pin : pins) {
                helper.pinDao.update(pin);
            }
            
            for (Pin pin : pins) {
                helper.loadableDao.update(pin.loadable);
            }
        } catch(SQLException e) {
            Logger.e("Error updating pins in db", e);
        }
    }
    
    public List<Pin> getPinned() {
        List<Pin> list = null;
        try {
             list = helper.pinDao.queryForAll();
             for (Pin p : list) {
                 helper.loadableDao.refresh(p.loadable);
             }
        } catch (SQLException e) {
            Logger.e("Error getting pins from db", e);
        }
        
        return list;
    }
}
