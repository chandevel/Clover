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

import com.google.gson.annotations.SerializedName;

public class ExportedPin {
    @SerializedName("archived")
    private final boolean archived;
    @SerializedName("pin_id")
    private final int pinId;
    @SerializedName("is_error")
    private final boolean isError;
    @SerializedName("loadable_id")
    private final int loadableId;
    @SerializedName("order")
    private final int order;
    @SerializedName("quote_last_count")
    private final int quoteLastCount;
    @SerializedName("quote_new_count")
    private final int quoteNewCount;
    @SerializedName("watch_last_count")
    private final int watchLastCount;
    @SerializedName("watch_new_count")
    private final int watchNewCount;
    @SerializedName("watching")
    private final boolean watching;
    @SerializedName("exported_loadable")
    private final ExportedLoadable exportedLoadable;

    public ExportedPin(
            boolean archived,
            int pinId,
            boolean isError,
            int loadableId,
            int order,
            int quoteLastCount,
            int quoteNewCount,
            int watchLastCount,
            int watchNewCount,
            boolean watching,
            @NonNull ExportedLoadable exportedLoadable
    ) {
        this.archived = archived;
        this.pinId = pinId;
        this.isError = isError;
        this.loadableId = loadableId;
        this.order = order;
        this.quoteLastCount = quoteLastCount;
        this.quoteNewCount = quoteNewCount;
        this.watchLastCount = watchLastCount;
        this.watchNewCount = watchNewCount;
        this.watching = watching;
        this.exportedLoadable = exportedLoadable;
    }

    public boolean isArchived() {
        return archived;
    }

    public int getPinId() {
        return pinId;
    }

    public boolean isError() {
        return isError;
    }

    public int getLoadableId() {
        return loadableId;
    }

    public int getOrder() {
        return order;
    }

    public int getQuoteLastCount() {
        return quoteLastCount;
    }

    public int getQuoteNewCount() {
        return quoteNewCount;
    }

    public int getWatchLastCount() {
        return watchLastCount;
    }

    public int getWatchNewCount() {
        return watchNewCount;
    }

    public boolean isWatching() {
        return watching;
    }

    public ExportedLoadable getExportedLoadable() {
        return exportedLoadable;
    }
}
