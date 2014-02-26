package org.floens.chan.model;

import android.content.Context;
import android.os.Bundle;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Something that can be loaded, like a board or thread.
 */
@DatabaseTable
public class Loadable {
    @DatabaseField(generatedId = true)
    private int id;
    
    @DatabaseField
    public int mode = Mode.INVALID;
    
    @DatabaseField
    public String board = "";
    
    @DatabaseField
    public int no = -1;
    
    @DatabaseField
    public String title = "";
    
    @DatabaseField
    public int listViewIndex;
    
    @DatabaseField
    public int listViewTop;
    
    /**
     * When simple mode is enabled, CPU intensive methods won't get called.
     * This is used for the thread watcher.
     */
    public boolean simpleMode = false;
    
    /**
     * Constructs an empty loadable.
     * The mode is INVALID.
     */
    public Loadable() {
    }
    
    /**
     * Quick constructor for a board loadable.
     * @param board
     */
    public Loadable(String board) {
        mode = Mode.BOARD;
        this.board = board;
        no = 0;
    }
    
    /**
     * Quick constructor for a thread loadable.
     * @param board
     * @param no
     */
    public Loadable(String board, int no) {
        mode = Mode.THREAD;
        this.board = board;
        this.no = no;
    }
    
    /**
     * Quick constructor for a thread loadable with an title.
     * @param board
     * @param no
     * @param title
     */
    public Loadable(String board, int no, String title) {
        mode = Mode.THREAD;
        this.board = board;
        this.no = no;
        this.title = title;
    }
    
    /**
     * Does not compare the title.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Loadable)) return false;
        
        Loadable other = (Loadable) object;
        
        return 
                mode == other.mode &&
                board.equals(other.board) &&
                no == other.no;
    }
    
    public boolean isBoardMode() {
        return mode == Mode.BOARD;
    }
    
    public boolean isThreadMode() {
        return mode == Mode.THREAD;
    }
    
    public boolean isCatalogMode() {
        return mode == Mode.CATALOG;
    }
    
    public void readFromBundle(Context context, Bundle bundle) {
        String p = context.getPackageName();
        mode = bundle.getInt(p + ".mode", Mode.INVALID);
        board = bundle.getString(p + ".board", "");
        no = bundle.getInt(p + ".no", -1);
        title = bundle.getString(p + ".subject", "");
        listViewIndex = bundle.getInt(p + ".listViewIndex");
        listViewTop = bundle.getInt(p + ".listViewTop");
    }
    
    public void writeToBundle(Context context, Bundle bundle) {
        String p = context.getPackageName();
        bundle.putInt(p + ".mode", mode);
        bundle.putString(p + ".board", board);
        bundle.putInt(p + ".no", no);
        bundle.putString(p + ".subject", title);
        bundle.putInt(p + ".listViewIndex", listViewIndex);
        bundle.putInt(p + ".listViewTop", listViewTop);
    }
    
    public Loadable copy() {
        Loadable copy = new Loadable();
        copy.mode = mode;
        copy.board = board;
        copy.no = no;
        copy.title = title;
        copy.listViewIndex = listViewIndex;
        copy.listViewTop = listViewTop;
        copy.simpleMode = simpleMode;
        
        return copy;
    }
    
    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        public static final int BOARD = 1;
        public static final int CATALOG = 2;
    }
}





