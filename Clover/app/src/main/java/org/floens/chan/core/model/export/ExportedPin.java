package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

public class ExportedPin {
    @SerializedName("archived")
    private boolean archived;
    @SerializedName("pin_id")
    private int pinId;
    @SerializedName("is_error")
    private boolean isError;
    @SerializedName("loadable_id")
    private int loadableId;
    @SerializedName("order")
    private int order;
    @SerializedName("quote_last_count")
    private int quoteLastCount;
    @SerializedName("quote_new_count")
    private int quoteNewCount;
    @SerializedName("thumbnail_url")
    private String thumbnailUrl;
    @SerializedName("watch_last_count")
    private int watchLastCount;
    @SerializedName("watch_new_count")
    private int watchNewCount;
    @SerializedName("watching")
    private boolean watching;
    @SerializedName("exported_loadable")
    private ExportedLoadable exportedLoadable;

    public ExportedPin(
            boolean archived,
            int pinId,
            boolean isError,
            int loadableId,
            int order,
            int quoteLastCount,
            int quoteNewCount,
            String thumbnailUrl,
            int watchLastCount,
            int watchNewCount,
            boolean watching,
            ExportedLoadable exportedLoadable
    ) {
        this.archived = archived;
        this.pinId = pinId;
        this.isError = isError;
        this.loadableId = loadableId;
        this.order = order;
        this.quoteLastCount = quoteLastCount;
        this.quoteNewCount = quoteNewCount;
        this.thumbnailUrl = thumbnailUrl;
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
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
