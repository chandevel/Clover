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

public class ExportedPostHide {
    @SerializedName("site")
    private final int site;
    @SerializedName("board")
    @Nullable
    private final String board;
    @SerializedName("no")
    private final int no;
    @SerializedName("wholeThread")
    private final boolean wholeThread;
    @SerializedName("hide")
    private final boolean hide;
    @SerializedName("hideRepliesToThisPost")
    private final boolean hideRepliesToThisPost;
    @SerializedName("threadNo")
    private final int threadNo;

    public ExportedPostHide(
            int site,
            @NonNull String board,
            int no,
            boolean wholeThread,
            boolean hide,
            boolean hideRepliesToThisPost,
            int threadNo
    ) {
        this.site = site;
        this.board = board;
        this.no = no;
        this.wholeThread = wholeThread;
        this.hide = hide;
        this.hideRepliesToThisPost = hideRepliesToThisPost;
        this.threadNo = threadNo;
    }

    public int getSite() {
        return site;
    }

    @Nullable
    public String getBoard() {
        return board;
    }

    public int getNo() {
        return no;
    }

    public boolean getWholeThread() {
        return wholeThread;
    }

    public boolean getHide() {
        return hide;
    }

    public boolean getHideRepliesToThisPost() {
        return hideRepliesToThisPost;
    }

    public int getThreadNo() {
        return threadNo;
    }
}
