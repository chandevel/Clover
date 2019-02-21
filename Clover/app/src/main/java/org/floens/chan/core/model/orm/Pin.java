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
package org.floens.chan.core.model.orm;

import android.support.annotation.NonNull;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "pin")
public class Pin implements Comparable<Pin>, Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable;

    @DatabaseField
    public boolean watching = true;

    @DatabaseField
    public int watchLastCount = -1;

    @DatabaseField
    public int watchNewCount = -1;

    @DatabaseField
    public int quoteLastCount = -1;

    @DatabaseField
    public int quoteNewCount = -1;

    @DatabaseField
    public boolean isError = false;

    @DatabaseField
    public String thumbnailUrl = null;

    @DatabaseField
    public int order = -1;

    @DatabaseField
    public boolean archived = false;

    public Pin() {
    }

    public int getNewPostCount() {
        if (watchLastCount < 0 || watchNewCount < 0) {
            return 0;
        } else {
            return Math.max(0, watchNewCount - watchLastCount);
        }
    }

    public int getNewQuoteCount() {
        if (quoteNewCount < 0 || quoteLastCount < 0) {
            return 0;
        } else {
            return Math.max(0, quoteNewCount - quoteLastCount);
        }
    }

    @Override
    public Pin clone() {
        Pin copy = new Pin();
        copy.id = id;
        copy.loadable = loadable;
        copy.watching = watching;
        copy.watchLastCount = watchLastCount;
        copy.watchNewCount = watchNewCount;
        copy.quoteLastCount = quoteLastCount;
        copy.quoteNewCount = quoteNewCount;
        copy.isError = isError;
        copy.thumbnailUrl = thumbnailUrl;
        copy.order = order;
        copy.archived = archived;
        return copy;
    }

    @Override
    public int compareTo(@NonNull Pin o) {
        int lhs = this.order;
        int rhs = o.order;
        return lhs - rhs;
    }
}
