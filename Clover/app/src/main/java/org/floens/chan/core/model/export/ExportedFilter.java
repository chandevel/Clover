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

import com.google.gson.annotations.SerializedName;

import org.floens.chan.core.manager.FilterType;

public class ExportedFilter {
    @SerializedName("enabled")
    private boolean enabled = true;
    @SerializedName("type")
    private int type = FilterType.SUBJECT.flag | FilterType.COMMENT.flag;
    @SerializedName("pattern")
    private String pattern;
    @SerializedName("all_boards")
    private boolean allBoards = true;
    @SerializedName("boards")
    private String boards = "";
    @SerializedName("action")
    private int action;
    @SerializedName("color")
    private int color;

    public ExportedFilter(
            boolean enabled,
            int type,
            String pattern,
            boolean allBoards,
            String boards,
            int action,
            int color
    ) {
        this.enabled = enabled;
        this.type = type;
        this.pattern = pattern;
        this.allBoards = allBoards;
        this.boards = boards;
        this.action = action;
        this.color = color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getType() {
        return type;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isAllBoards() {
        return allBoards;
    }

    public String getBoards() {
        return boards;
    }

    public int getAction() {
        return action;
    }

    public int getColor() {
        return color;
    }
}
