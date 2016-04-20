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
import android.text.TextUtils;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Something that can be loaded, like a board or thread.
 */
@DatabaseTable
public class Loadable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField
    public int mode = Mode.INVALID;

    @DatabaseField(canBeNull = false, index = true)
    public String board;

    @DatabaseField(index = true)
    public int no = -1;

    @DatabaseField(canBeNull = false)
    public String title = "";

    @DatabaseField
    public int listViewIndex;

    @DatabaseField
    public int listViewTop;

    @DatabaseField
    public int lastViewed = -1;

    @DatabaseField
    public int lastLoaded = -1;

    public int markedNo = -1;

    // when the title, listViewTop, listViewIndex or lastViewed were changed
    public boolean dirty = false;

    /**
     * Constructs an empty loadable. The mode is INVALID.
     */
    private Loadable() {
    }

    public static Loadable emptyLoadable() {
        return new Loadable();
    }

    public static Loadable forCatalog(String board) {
        Loadable loadable = new Loadable();
        loadable.mode = Mode.CATALOG;
        loadable.board = board;
        return loadable;
    }

    public static Loadable forThread(String board, int no) {
        return Loadable.forThread(board, no, "");
    }

    public static Loadable forThread(String board, int no, String title) {
        Loadable loadable = new Loadable();
        loadable.mode = Mode.THREAD;
        loadable.board = board;
        loadable.no = no;
        loadable.title = title;
        return loadable;
    }

    public void setTitle(String title) {
        if (!TextUtils.equals(this.title, title)) {
            this.title = title;
            dirty = true;
        }
    }

    public void setLastViewed(int lastViewed) {
        if (this.lastViewed != lastViewed) {
            this.lastViewed = lastViewed;
            dirty = true;
        }
    }

    public void setLastLoaded(int lastLoaded) {
        if (this.lastLoaded != lastLoaded) {
            this.lastLoaded = lastLoaded;
            dirty = true;
        }
    }

    public void setListViewTop(int listViewTop) {
        if (this.listViewTop != listViewTop) {
            this.listViewTop = listViewTop;
            dirty = true;
        }
    }

    public void setListViewIndex(int listViewIndex) {
        if (this.listViewIndex != listViewIndex) {
            this.listViewIndex = listViewIndex;
            dirty = true;
        }
    }

    /**
     * Compares the mode, board and no.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Loadable))
            return false;

        Loadable other = (Loadable) object;

        if (mode == other.mode) {
            switch (mode) {
                case Mode.INVALID:
                    return true;
                case Mode.CATALOG:
                case Mode.BOARD:
                    return board.equals(other.board);
                case Mode.THREAD:
                    return board.equals(other.board) && no == other.no;
                default:
                    throw new IllegalArgumentException();
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = mode;

        if (mode == Mode.THREAD || mode == Mode.CATALOG || mode == Mode.BOARD) {
            result = 31 * result + (board != null ? board.hashCode() : 0);
        }
        if (mode == Mode.THREAD) {
            result = 31 * result + no;
        }
        return result;
    }

    @Override
    public String toString() {
        return "Loadable{" +
                "id=" + id +
                ", mode=" + mode +
                ", board='" + board + '\'' +
                ", no=" + no +
                ", title='" + title + '\'' +
                ", listViewIndex=" + listViewIndex +
                ", listViewTop=" + listViewTop +
                ", lastViewed=" + lastViewed +
                ", lastLoaded=" + lastLoaded +
                ", markedNo=" + markedNo +
                ", dirty=" + dirty +
                '}';
    }

    public boolean isThreadMode() {
        return mode == Mode.THREAD;
    }

    public boolean isCatalogMode() {
        return mode == Mode.CATALOG;
    }

    public static Loadable readFromParcel(Parcel parcel) {
        Loadable loadable = new Loadable();
        /*loadable.id = */
        parcel.readInt();
        loadable.mode = parcel.readInt();
        loadable.board = parcel.readString();
        loadable.no = parcel.readInt();
        loadable.title = parcel.readString();
        loadable.listViewIndex = parcel.readInt();
        loadable.listViewTop = parcel.readInt();
        return loadable;
    }

    public void writeToParcel(Parcel parcel) {
        parcel.writeInt(id);
        parcel.writeInt(mode);
        parcel.writeString(board);
        parcel.writeInt(no);
        parcel.writeString(title);
        parcel.writeInt(listViewIndex);
        parcel.writeInt(listViewTop);
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
        copy.lastLoaded = lastLoaded;

        return copy;
    }

    public static class Mode {
        public static final int INVALID = -1;
        public static final int THREAD = 0;
        @Deprecated
        public static final int BOARD = 1;
        public static final int CATALOG = 2;
    }
}
