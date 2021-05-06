/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.model.orm;

import android.os.Parcel;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.HttpUrlType;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.DummySite;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.Mode.CATALOG;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.Mode.INVALID;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.Mode.THREAD;
import static com.github.adamantcheese.chan.utils.StringUtils.maskPostNo;

/**
 * Something that can be loaded, like a board or thread.
 * Used instead of {@link Board} or {@link Post} because of the unique things a loadable can do and save in the database:<br>
 * - It keeps track of the list index where the user last viewed.<br>
 * - It keeps track of what post was last seen and loaded.<br>
 * - It keeps track of the title the toolbar should show, generated from the first post (so after loading).<br>
 * <p>Obtain Loadables through {@link com.github.adamantcheese.chan.core.database.DatabaseLoadableManager} to make sure everyone
 * references the same loadable and that the loadable is properly saved in the database.
 */
@DatabaseTable(tableName = "loadable")
public class Loadable
        implements Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = "site")
    public int siteId;

    public transient Site site;

    /**
     * Mode for the loadable. Either thread or catalog.
     */
    @DatabaseField
    public int mode = INVALID;

    @DatabaseField(columnName = "board", canBeNull = false, index = true)
    public String boardCode = "";

    public Board board;

    /**
     * Thread number.
     */
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

    @DatabaseField(canBeNull = false, dataType = DataType.DATE_STRING, format = "yyyy-MM-dd HH:mm:ss")
    public Date lastLoadDate = GregorianCalendar.getInstance().getTime();

    @DatabaseField(persisterClass = HttpUrlType.class)
    public HttpUrl thumbnailUrl;

    public int markedNo = -1;

    // a reply draft that is specific to this loadable and is not stored in the database
    public final Reply draft = new Reply();

    /**
     * Constructs an empty loadable. The mode is INVALID.
     */
    protected Loadable() {
    }

    public static Loadable importLoadable(
            int siteId,
            int mode,
            String boardCode,
            int no,
            String title,
            int listViewIndex,
            int listViewTop,
            int lastViewed,
            int lastLoaded
    ) {
        Loadable loadable = new Loadable();
        loadable.siteId = siteId;
        loadable.mode = mode;
        loadable.boardCode = boardCode;
        loadable.no = no;
        loadable.title = title;
        loadable.listViewIndex = listViewIndex;
        loadable.listViewTop = listViewTop;
        loadable.lastViewed = lastViewed;
        loadable.lastLoaded = lastLoaded;

        return loadable;
    }

    public static Loadable emptyLoadable() {
        Loadable ret = new Loadable();
        ret.site = new DummySite();
        ret.board = Board.getDummyBoard();
        return ret;
    }

    public static Loadable forCatalog(Board board) {
        Loadable loadable = new Loadable();
        loadable.siteId = board.siteId;
        loadable.site = board.site;
        loadable.board = board;
        loadable.boardCode = board.code;
        loadable.mode = CATALOG;
        return loadable;
    }

    public static Loadable forThread(Board board, int no, String title) {
        return forThread(board, no, title, true);
    }

    /**
     * DON'T USE THIS METHOD WITH addToDatabase SET TO false UNLESS YOU KNOW WHAT YOU'RE DOING.
     */
    public static Loadable forThread(Board board, int no, String title, boolean addToDatabase) {
        Loadable loadable = new Loadable();
        loadable.siteId = board.siteId;
        loadable.site = board.site;
        loadable.mode = THREAD;
        loadable.board = board;
        loadable.boardCode = board.code;
        loadable.no = no;
        loadable.title = title;
        if (!addToDatabase) return loadable;
        return instance(DatabaseLoadableManager.class).get(loadable);
    }

    /**
     * Compares the mode, site, board and no.
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Loadable)) return false;

        Loadable other = (Loadable) object;

        if (siteId != other.siteId) {
            return false;
        }

        if (mode == other.mode) {
            switch (mode) {
                case INVALID:
                    return true;
                case CATALOG:
                    return boardCode.equals(other.boardCode);
                case THREAD:
                    return boardCode.equals(other.boardCode) && no == other.no;
                default:
                    throw new IllegalArgumentException();
            }
        } else {
            return false;
        }
    }

    public boolean databaseEquals(Loadable other) {
        return this.id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, mode != INVALID ? boardCode : 0, mode == THREAD ? no : 0, siteId);
    }

    @Override
    @NonNull
    public String toString() {
        return "Loadable{mode=" + mode + ", board='" + boardCode + '\'' + ", no=" + maskPostNo(no) + '\''
                + ", listViewIndex=" + listViewIndex + ", listViewTop=" + listViewTop + ", lastViewed=" + maskPostNo(
                lastViewed) + ", lastLoaded=" + maskPostNo(lastLoaded) + ", markedNo=" + maskPostNo(markedNo) + '}';
    }

    public boolean isThreadMode() {
        return mode == THREAD;
    }

    public boolean isCatalogMode() {
        return mode == CATALOG;
    }

    /**
     * @return a string that really condenses the loadable contents for user-facing display
     */
    public String toShortestString() {
        return TextUtils.isEmpty(title) ? String.format(Locale.ENGLISH, "/%s/%d", boardCode, no) : title;
    }

    public String desktopUrl() {
        return site.resolvable().desktopUrl(this, no);
    }

    public String desktopUrl(Post post) {
        return site.resolvable().desktopUrl(this, post.no);
    }

    public static Loadable readFromParcel(Parcel parcel) {
        Loadable loadable = new Loadable();
        loadable.siteId = parcel.readInt();
        loadable.mode = parcel.readInt();
        loadable.boardCode = parcel.readString();
        loadable.no = parcel.readInt();
        loadable.title = parcel.readString();
        loadable.listViewIndex = parcel.readInt();
        loadable.listViewTop = parcel.readInt();
        String s = parcel.readString();
        loadable.thumbnailUrl = TextUtils.isEmpty(s) ? null : HttpUrl.get(s);
        return loadable;
    }

    public void writeToParcel(Parcel parcel) {
        // TODO(multi-site)
        parcel.writeInt(siteId);
        parcel.writeInt(mode);
        // TODO(multi-site)
        parcel.writeString(boardCode);
        parcel.writeInt(no);
        parcel.writeString(title);
        parcel.writeInt(listViewIndex);
        parcel.writeInt(listViewTop);
        parcel.writeString(thumbnailUrl == null ? "" : thumbnailUrl.toString());
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Loadable clone() {
        Loadable copy = new Loadable();
        copy.id = id;
        copy.siteId = siteId;
        copy.site = site;
        copy.mode = mode;
        if (board != null) copy.board = board.clone();
        copy.boardCode = boardCode;
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
        public static final int CATALOG = 1;
    }
}
