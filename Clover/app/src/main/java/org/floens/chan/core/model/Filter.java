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

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.floens.chan.core.manager.FilterEngine;

import java.util.regex.Matcher;

@DatabaseTable
public class Filter {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false)
    public boolean enabled = true;

    @DatabaseField(canBeNull = false)
    public int type = FilterEngine.FilterType.COMMENT.id;

    @DatabaseField(canBeNull = false)
    public String pattern;

    @DatabaseField(canBeNull = false)
    public boolean allBoards = true;

    @DatabaseField(canBeNull = false)
    public String boards;

    @DatabaseField(canBeNull = false)
    public int action;

    @DatabaseField(canBeNull = false)
    public int color;

    public final Object compiledMatcherLock = new Object();

    /**
     * Cached version of {@link #pattern} compiled by {@link org.floens.chan.core.manager.FilterEngine#compile(String)}.
     * Thread safe when synchronized on {@link #compiledMatcherLock}
     */
    public Matcher compiledMatcher;

    public void apply(Filter filter) {
        enabled = filter.enabled;
        type = filter.type;
        pattern = filter.pattern;
        allBoards = filter.allBoards;
        boards = filter.boards;
        action = filter.action;
        color = filter.color;
    }
}
