package org.floens.chan.database;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import android.content.Context;

public class DatabaseManager {
    private static final String TAG = "DatabaseManager";

    private final DatabaseHelper helper;
    private List<SavedReply> savedReplies;

    public DatabaseManager(Context context) {
        helper = new DatabaseHelper(context);
    }

    public void saveReply(SavedReply saved) {
        Logger.e(TAG, "Saving " + saved.board + ", " + saved.no);

        try {
            helper.savedDao.create(saved);
        } catch (SQLException e) {
            Logger.e(TAG, "Error saving reply", e);
        }

        loadSavedReplies();
    }

    public SavedReply getSavedReply(String board, int no) {
        if (savedReplies == null) {
            loadSavedReplies();
        }

        // TODO: optimize this
        for (SavedReply r : savedReplies) {
            if (r.board.equals(board) && r.no == no) {
                return r;
            }
        }

        return null;
    }

    public boolean isSavedReply(String board, int no) {
        return getSavedReply(board, no) != null;
    }

    private void loadSavedReplies() {
        // TODO trim the table if it gets too large
        try {
            savedReplies = helper.savedDao.queryForAll();
        } catch (SQLException e) {
            Logger.e(TAG, "Error loading saved replies", e);
        }
    }

    public void addPin(Pin pin) {
        try {
            helper.loadableDao.create(pin.loadable);
            helper.pinDao.create(pin);
        } catch (SQLException e) {
            Logger.e(TAG, "Error adding pin to db", e);
        }
    }

    public void removePin(Pin pin) {
        try {
            helper.pinDao.delete(pin);
            helper.loadableDao.delete(pin.loadable);
        } catch (SQLException e) {
            Logger.e(TAG, "Error removing pin from db", e);
        }
    }

    public void updatePin(Pin pin) {
        try {
            helper.pinDao.update(pin);
            helper.loadableDao.update(pin.loadable);
        } catch (SQLException e) {
            Logger.e(TAG, "Error updating pin in db", e);
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
        } catch (SQLException e) {
            Logger.e(TAG, "Error updating pins in db", e);
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
            Logger.e(TAG, "Error getting pins from db", e);
        }

        return list;
    }

    public void setBoards(final List<Board> boards) {
        try {
            helper.boardsDao.callBatchTasks(new Callable<Void>() {
                @Override
                public Void call() throws SQLException {
                    for (Board b : boards) {
                        helper.boardsDao.createOrUpdate(b);
                    }
                    
                    return null;
                }
            });
        } catch (Exception e) {
            Logger.e(TAG, "Error setting boards in db", e);
        }
    }
    
    public void updateBoards(final List<Board> boards) {
        try {
            helper.boardsDao.callBatchTasks(new Callable<Void>() {
                @Override
                public Void call() throws SQLException {
                    long start = Time.get();
                    for (Board b : boards) {
                        helper.boardsDao.update(b);
                    }
                    
                    Logger.d(TAG, "Update board took " + Time.get(start));
                    
                    return null;
                }
            });
        } catch (Exception e) {
            Logger.e(TAG, "Error updating boards in db", e);
        }
    }

    public List<Board> getBoards() {
        List<Board> boards = null;
        try {
            boards = helper.boardsDao.queryForAll();
        } catch (SQLException e) {
            Logger.e(TAG, "Error getting boards from db", e);
        }
        
        return boards;
    }

    public String getSummary() {
        String o = "";

        try {
            o += "Loadable rows: " + helper.loadableDao.countOf() + "\n";
            o += "Pin rows: " + helper.pinDao.countOf() + "\n";
            o += "SavedReply rows: " + helper.savedDao.countOf() + "\n";
            o += "Board rows: " + helper.boardsDao.countOf() + "\n";
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return o;
    }

    public void reset() {
        helper.reset();
        loadSavedReplies();
    }
}
