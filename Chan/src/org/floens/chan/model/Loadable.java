package org.floens.chan.model;

import android.content.Context;
import android.os.Bundle;

/**
 * Something that can be loaded, like a board or thread.
 */
public class Loadable {
    public int mode = Mode.INVALID;
    public String board = "";
    public int no = -1;
    public String title = "";
    public int listViewIndex;
    public int listViewTop;
    
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
    
    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        public static final int BOARD = 1;
        public static final int CATALOG = 2;
    }
}





