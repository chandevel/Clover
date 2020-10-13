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
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.AlreadyDownloaded;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.DownloadingAndNotViewable;
import static com.github.adamantcheese.chan.core.model.orm.Loadable.LoadableDownloadingState.DownloadingAndViewable;
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
    public int mode = Mode.INVALID;

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
     * Tells us whether this loadable (when in THREAD mode) contains information about
     * a live thread or the local saved copy of a thread (which may be already deleted from the server)
     */
    private transient LoadableDownloadingState loadableDownloadingState = LoadableDownloadingState.NotDownloading;

    public synchronized void setLoadableState(LoadableDownloadingState state) {
        this.loadableDownloadingState = state;
    }

    public synchronized LoadableDownloadingState getLoadableDownloadingState() {
        return loadableDownloadingState;
    }

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
        return new Loadable();
    }

    public static Loadable forCatalog(Board board) {
        Loadable loadable = new Loadable();
        loadable.siteId = board.siteId;
        loadable.site = board.site;
        loadable.board = board;
        loadable.boardCode = board.code;
        loadable.mode = Mode.CATALOG;
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
        loadable.mode = Mode.THREAD;
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
                case Mode.INVALID:
                    return true;
                case Mode.CATALOG:
                    return boardCode.equals(other.boardCode);
                case Mode.THREAD:
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
        int result = mode + 1;

        if (mode == Mode.THREAD || mode == Mode.CATALOG) {
            result = 31 * result + boardCode.hashCode();
        }
        if (mode == Mode.THREAD) {
            result = 31 * result + no;
        }
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "Loadable{mode=" + mode + ", board='" + boardCode + '\'' + ", no=" + maskPostNo(no) + '\''
                + ", listViewIndex=" + listViewIndex + ", listViewTop=" + listViewTop + ", lastViewed=" + maskPostNo(
                lastViewed) + ", lastLoaded=" + maskPostNo(lastLoaded) + ", markedNo=" + maskPostNo(markedNo)
                + ", loadableDownloadingState=" + loadableDownloadingState.name() + '}';
    }

    public boolean isThreadMode() {
        return mode == Mode.THREAD;
    }

    public boolean isCatalogMode() {
        return mode == Mode.CATALOG;
    }

    /**
     * Thread is either fully downloaded or it is still being downloaded BUT we are currently
     * viewing the local copy of the thread
     */
    public boolean isLocal() {
        return loadableDownloadingState == DownloadingAndViewable || loadableDownloadingState == AlreadyDownloaded;
    }

    /**
     * Thread is being downloaded but we are not currently viewing the local copy
     */
    public boolean isDownloading() {
        return loadableDownloadingState == DownloadingAndNotViewable;
    }

    /**
     * @return a string with only the info that we are interested in from this loadable
     */
    public String toShortString() {
        return String.format(Locale.ENGLISH, "[%s, %s, %s]", site.name(), boardCode, maskPostNo(no));
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

    /**
     * Only for Loadable.Mode == THREAD
     */
    public enum LoadableDownloadingState {
        /**
         * We are not downloading a thread associated with this loadable
         */
        NotDownloading,
        /**
         * We are downloading this thread, but we are not viewing it at the current time.
         * (We are viewing the live thread)
         */
        DownloadingAndNotViewable,
        /**
         * We are downloading this thread and we are currently viewing it (We are viewing the local
         * thread)
         */
        DownloadingAndViewable,
        /**
         * Thread has been fully downloaded so it's always a local thread
         */
        AlreadyDownloaded,
    }
}
