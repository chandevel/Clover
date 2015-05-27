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

import android.os.Parcel;

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

    public int markedNo = -1;

    /**
     * Constructs an empty loadable. The mode is INVALID.
     */
    public Loadable() {
    }

    public Loadable(String board) {
        mode = Mode.CATALOG;
        this.board = board;
        this.no = 0;
    }

    /**
     * Quick constructor for a thread loadable.
     */
    public Loadable(String board, int no) {
        mode = Mode.THREAD;
        this.board = board;
        this.no = no;
    }

    /**
     * Quick constructor for a thread loadable with an title.
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

    @Override
    public int hashCode() {
        int result = mode;
        result = 31 * result + (board != null ? board.hashCode() : 0);
        result = 31 * result + no;
        return result;
    }

    public boolean isThreadMode() {
        return mode == Mode.THREAD;
    }

    public boolean isCatalogMode() {
        return mode == Mode.CATALOG;
    }

    public void writeToParcel(Parcel parcel) {
        parcel.writeInt(mode);
        parcel.writeString(board);
        parcel.writeInt(no);
        parcel.writeString(title);
        parcel.writeInt(listViewIndex);
        parcel.writeInt(listViewTop);
    }

    public void readFromParcel(Parcel parcel) {
        mode = parcel.readInt();
        board = parcel.readString();
        no = parcel.readInt();
        title = parcel.readString();
        listViewIndex = parcel.readInt();
        listViewTop = parcel.readInt();
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

    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        public static final int BOARD = 1;
        public static final int CATALOG = 2;
    }
}
