/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.model;

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

    public int lastViewed = -1;

    /**
     * Constructs an empty loadable. The mode is INVALID.
     */
    public Loadable() {
    }

    /**
     * Quick constructor for a board loadable.
     *
     * @param board
     */
    public Loadable(String board) {
        mode = Mode.BOARD;
        this.board = board;
        no = 0;
    }

    /**
     * Quick constructor for a thread loadable.
     *
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
     *
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
     * Compares the mode, board and no.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Loadable))
            return false;

        Loadable other = (Loadable) object;

        return mode == other.mode && board.equals(other.board) && no == other.no;
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

    public void readFromBundle(Context context, String tag, Bundle bundle) {
        String p = context.getPackageName();
        mode = bundle.getInt(p + "." + tag + ".mode", Mode.INVALID);
        board = bundle.getString(p + "." + tag + ".board", "");
        no = bundle.getInt(p + "." + tag + ".no", -1);
        title = bundle.getString(p + "." + tag + ".subject", "");
        listViewIndex = bundle.getInt(p + "." + tag + ".listViewIndex");
        listViewTop = bundle.getInt(p + "." + tag + ".listViewTop");
    }

    public void writeToBundle(Context context, String tag, Bundle bundle) {
        String p = context.getPackageName();
        bundle.putInt(p + "." + tag + ".mode", mode);
        bundle.putString(p + "." + tag + ".board", board);
        bundle.putInt(p + "." + tag + ".no", no);
        bundle.putString(p + "." + tag + ".subject", title);
        bundle.putInt(p + "." + tag + ".listViewIndex", listViewIndex);
        bundle.putInt(p + "." + tag + ".listViewTop", listViewTop);
    }

    public Loadable copy() {
        Loadable copy = new Loadable();
        copy.mode = mode;
        copy.board = board;
        copy.no = no;
        copy.title = title;
        copy.listViewIndex = listViewIndex;
        copy.listViewTop = listViewTop;
        copy.lastViewed = lastViewed;

        return copy;
    }

    public void generateTitle(Post post) {
        title = Post.generateTitle(post);
    }

    public void generateTitle() {
        if (mode == Mode.CATALOG) {
            title = "/" + board + "/";
        } else {
            title = "/" + board + "/" + no;
        }
    }

    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        public static final int BOARD = 1;
        public static final int CATALOG = 2;
    }
}
