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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.manager.FilterType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "filter")
public class Filter
        implements Cloneable {

    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false)
    public boolean enabled = true;

    // Flags of FilterTypes that this filter is applied to
    @DatabaseField(canBeNull = false)
    public int type = FilterType.SUBJECT.flag | FilterType.COMMENT.flag;

    @DatabaseField(canBeNull = false)
    public String pattern;

    @DatabaseField(columnName = "negative_pattern", canBeNull = false)
    public String negativePattern = "";

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

    @DatabaseField(canBeNull = false)
    public int order = -1;

    @DatabaseField(canBeNull = false)
    public boolean onlyOnOP;

    @DatabaseField(canBeNull = false)
    public boolean applyToSaved;

    @DatabaseField(canBeNull = false)
    public String label = "";

    public boolean hasFilter(FilterType filterType) {
        return (type & filterType.flag) != 0;
    }

    public Filter() {
    }

    public Filter(
            boolean enabled,
            int type,
            String pattern,
            String negativePattern,
            boolean allBoards,
            String boards,
            int action,
            int color,
            boolean applyToReplies,
            int order,
            boolean onlyOnOP,
            boolean applyToSaved,
            String label
    ) {
        this.enabled = enabled;
        this.type = type;
        this.pattern = pattern;
        this.negativePattern = negativePattern;
        this.allBoards = allBoards;
        this.boards = boards;
        this.action = action;
        this.color = color;
        this.applyToReplies = applyToReplies;
        this.order = order;
        this.onlyOnOP = onlyOnOP;
        this.applyToSaved = applyToSaved;
        this.label = label;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    public Filter clone() {
        return new Filter(
                enabled,
                type,
                pattern,
                negativePattern,
                allBoards,
                boards,
                action,
                color,
                applyToReplies,
                order,
                onlyOnOP,
                applyToSaved,
                label
        );
    }
}
