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

import com.github.adamantcheese.chan.core.manager.FilterType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "filter")
public class Filter implements Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false)
    public boolean enabled = true;

    // Flags of FilterTypes that this filter is applied to
    @DatabaseField(canBeNull = false)
    public int type = FilterType.SUBJECT.flag | FilterType.COMMENT.flag;

    @DatabaseField(canBeNull = false)
    public String pattern;

    @DatabaseField(canBeNull = false)
    public boolean allBoards = true;

    @DatabaseField(canBeNull = false)
    public String boards = "";

    @DatabaseField(canBeNull = false)
    public int action;

    @DatabaseField(canBeNull = false)
    public int color;

    @DatabaseField(columnName = "apply_to_replies", canBeNull = false)
    public boolean applyToReplies;

    public boolean hasFilter(FilterType filterType) {
        return (type & filterType.flag) != 0;
    }

    public String[] boardCodesNoId() {
        String[] boards = this.boards.split(",");
        for (int i = 0; i < boards.length; i++) {
            String s = boards[i];
            boards[i] = s.substring(s.indexOf(":") + 1);
        }
        return boards;
    }

    public Filter() {
    }

    public Filter(boolean enabled, int type, String pattern, boolean allBoards, String boards, int action, int color, boolean applyToReplies) {
        this.enabled = enabled;
        this.type = type;
        this.pattern = pattern;
        this.allBoards = allBoards;
        this.boards = boards;
        this.action = action;
        this.color = color;
        this.applyToReplies = applyToReplies;
    }

    public void apply(Filter filter) {
        enabled = filter.enabled;
        type = filter.type;
        pattern = filter.pattern;
        allBoards = filter.allBoards;
        boards = filter.boards;
        action = filter.action;
        color = filter.color;
        applyToReplies = filter.applyToReplies;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Filter clone() {
        return new Filter(
                enabled,
                type,
                pattern,
                allBoards,
                boards,
                action,
                color,
                applyToReplies
        );
    }
}
