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
package org.floens.chan.core.model.export;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class ExportedPostHide {
    @SerializedName("site")
    private int site;
    @SerializedName("board")
    @Nullable
    private String board;
    @SerializedName("no")
    private int no;

    //TODO: add "wholeThread" field when this task
    // https://github.com/Floens/Clover/pull/654/files#diff-5e9a09d29a1b99ff8a11eea249800b96R44 is merged

    public ExportedPostHide(int site, @NonNull String board, int no) {
        this.site = site;
        this.board = board;
        this.no = no;
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
}
