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
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

@DatabaseTable(tableName = "pin")
public class Pin
        implements Comparable<Pin>, Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable;

    // Is this pin being watched by the WatchManager?
    // Note that a pin's watched or not-watched state is different than a pin existing at all
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
    public int order = -1;

    @DatabaseField
    public boolean archived = false;

    //local field for keeping track of if the thread is a sticky; don't put this in the database
    public boolean isSticky = false;

    //local field for pin highlighting consistency; don't put this in the database
    public boolean drawerHighlight = false;

    public Pin() {
    }

    public Pin(
            @NonNull Loadable loadable,
            boolean watching,
            int watchLastCount,
            int watchNewCount,
            int quoteLastCount,
            int quoteNewCount,
            boolean isError,
            int order,
            boolean archived
    ) {
        this.loadable = loadable;
        this.watching = watching;
        this.watchLastCount = watchLastCount;
        this.watchNewCount = watchNewCount;
        this.quoteLastCount = quoteLastCount;
        this.quoteNewCount = quoteNewCount;
        this.isError = isError;
        this.order = order;
        this.archived = archived;
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

    @SuppressWarnings("MethodDoesntCallSuperMethod")
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
        copy.order = order;
        copy.archived = archived;
        return copy;
    }

    @Override
    public int compareTo(@NonNull Pin o) {
        return this.order - o.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(loadable.id, id);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }

        if (this == obj) {
            return true;
        }

        if (obj.getClass() != this.getClass()) {
            return false;
        }

        Pin other = (Pin) obj;

        return this.id == other.id && this.loadable.id == other.loadable.id;
    }

    @NonNull
    @Override
    public String toString() {
        return "[id = " + id + ", isError = " + isError + ", isArchived = " + archived + ", watching = " + watching
                + ", (active) = " + (!isError && !archived) + ", no = " + loadable.no + "]";
    }
}
