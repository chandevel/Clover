package com.github.adamantcheese.chan.core.model.export;

import androidx.annotation.NonNull;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public class ExportedSavedThread {
    @SerializedName("loadable_id")
    public int loadableId;
    @SerializedName("last_saved_post_no")
    public int lastSavedPostNo;
    @SerializedName("is_fully_downloaded")
    public boolean isFullyDownloaded;
    @SerializedName("is_stopped")
    public boolean isStopped;

    public ExportedSavedThread(int loadableId, int lastSavedPostNo, boolean isFullyDownloaded, boolean isStopped) {
        this.loadableId = loadableId;
        this.lastSavedPostNo = lastSavedPostNo;
        this.isFullyDownloaded = isFullyDownloaded;
        this.isStopped = isStopped;
    }

    public int getLoadableId() {
        return loadableId;
    }

    public int getLastSavedPostNo() {
        return lastSavedPostNo;
    }

    public boolean isFullyDownloaded() {
        return isFullyDownloaded;
    }

    public boolean isStopped() {
        return isStopped;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US,
                             "loadableId: %d, lastSavedPostNo: %d, isFullyDownloaded: %b, isStopped: %b",
                             loadableId,
                             lastSavedPostNo,
                             isFullyDownloaded,
                             isStopped
        );
    }
}
