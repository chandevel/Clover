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
package com.github.adamantcheese.chan.core.model.export;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

import okhttp3.HttpUrl;

public class ExportedLoadable {
    @SerializedName("board_code")
    private final String boardCode;
    @SerializedName("loadable_id")
    private final long loadableId;
    @SerializedName("last_loaded")
    private final int lastLoaded;
    @SerializedName("last_viewed")
    private final int lastViewed;
    @SerializedName("list_view_index")
    private final int listViewIndex;
    @SerializedName("list_view_top")
    private final int listViewTop;
    @SerializedName("mode")
    private final int mode;
    @SerializedName("no")
    private final int no;
    @SerializedName("site_id")
    private final int siteId;
    @SerializedName("title")
    @Nullable
    private final String title;
    @SerializedName("thumbnail_url")
    @Nullable
    private final String thumbnailUrl;

    public ExportedLoadable(
            String boardCode,
            long loadableId,
            int lastLoaded,
            int lastViewed,
            int listViewIndex,
            int listViewTop,
            int mode,
            int no,
            int siteId,
            @NonNull String title,
            String thumbnailUrl
    ) {
        this.boardCode = boardCode;
        this.loadableId = loadableId;
        this.lastLoaded = lastLoaded;
        this.lastViewed = lastViewed;
        this.listViewIndex = listViewIndex;
        this.listViewTop = listViewTop;
        this.mode = mode;
        this.no = no;
        this.siteId = siteId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getBoardCode() {
        return boardCode;
    }

    public long getLoadableId() {
        return loadableId;
    }

    public int getLastLoaded() {
        return lastLoaded;
    }

    public int getLastViewed() {
        return lastViewed;
    }

    public int getListViewIndex() {
        return listViewIndex;
    }

    public int getListViewTop() {
        return listViewTop;
    }

    public int getMode() {
        return mode;
    }

    public int getNo() {
        return no;
    }

    public int getSiteId() {
        return siteId;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public HttpUrl getThumbnailUrl() {
        return thumbnailUrl == null ? null : HttpUrl.get(thumbnailUrl);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(
                Locale.ENGLISH,
                "boardCode = %s, loadableId = %d, no = %d, mode= %d, siteId = %d, title = %s, thumbnailUrl = %s",
                boardCode,
                loadableId,
                no,
                mode,
                siteId,
                title,
                thumbnailUrl
        );
    }
}