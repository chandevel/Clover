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

    /**
     * Pins can now be used to either watch new posts or save new posts or do both
     * */
    @DatabaseField(columnName = "pin_type")
    public int pinType;

    public Pin() {
    }

    public Pin(
            Loadable loadable,
            boolean watching,
            int watchLastCount,
            int watchNewCount,
            int quoteLastCount,
            int quoteNewCount,
            boolean isError,
            String thumbnailUrl,
            int order,
            boolean archived,
            PinType pinType
    ) {
        this.loadable = loadable;
        this.watching = watching;
        this.watchLastCount = watchLastCount;
        this.watchNewCount = watchNewCount;
        this.quoteLastCount = quoteLastCount;
        this.quoteNewCount = quoteNewCount;
        this.isError = isError;
        this.thumbnailUrl = thumbnailUrl;
        this.order = order;
        this.archived = archived;
        this.pinType = pinType.typeValue;
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
        copy.thumbnailUrl = thumbnailUrl;
        copy.order = order;
        copy.archived = archived;
        copy.pinType = pinType;
        return copy;
    }

    @Override
    public int compareTo(@NonNull Pin o) {
        return this.order - o.order;
    }

    public enum PinType {
        WatchNewPosts(1 << 0),
        DownloadNewPosts(1 << 1),
        WatchAndDownload(WatchNewPosts.typeValue | DownloadNewPosts.typeValue);

        private int typeValue;

        public int getTypeValue() {
            return typeValue;
        }

        public boolean hasDownloadFlag() {
            return (typeValue & DownloadNewPosts.typeValue) != 0;
        }

        PinType(int typeValue) {
            this.typeValue = typeValue;
        }

        public static PinType from(int value) {
            if (value == WatchNewPosts.typeValue) {
                return WatchNewPosts;
            } else if (value == DownloadNewPosts.typeValue) {
                return DownloadNewPosts;
            } else if (value == WatchAndDownload.typeValue) {
                return WatchAndDownload;
            }

            throw new IllegalArgumentException("Not implemented for " + value);
        }
    }
}
