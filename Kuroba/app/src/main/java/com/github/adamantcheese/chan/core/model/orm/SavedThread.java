package com.github.adamantcheese.chan.core.model.orm;

import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "saved_thread")
public class SavedThread {
    public static final String ID = "id";
    public static final String PIN_ID = "pin_id";
    public static final String LAST_SAVED_POST_NO = "last_saved_post_no";
    public static final String IS_FULLY_DOWNLOADED = "is_fully_downloaded";
    public static final String IS_STOPPED = "is_stopped";

    @DatabaseField(columnName = ID, generatedId = true)
    public int id;

    @DatabaseField(columnName = PIN_ID)
    public int pinId;

    @DatabaseField(columnName = LAST_SAVED_POST_NO)
    public int lastSavedPostNo;

    /**
     * isFullyDownloaded will be true when a thread gets 404ed. That means that there will be no
     * new posts so we can stop trying to fetch new posts for this thread.
     * */
    @DatabaseField(columnName = IS_FULLY_DOWNLOADED)
    public boolean isFullyDownloaded;

    /**
     * User may stop saving a thread by clicking the corresponding button in the thread toolbar
     * */
    @DatabaseField(columnName = IS_STOPPED)
    public boolean isStopped;

    public SavedThread() {
    }

    public SavedThread(int pinId, boolean isFullyDownloaded, boolean isStopped) {
        this.pinId = pinId;
        this.isFullyDownloaded = isFullyDownloaded;
        this.isStopped = isStopped;
    }

    public void update(SavedThread other) {
        this.pinId = other.pinId;
        this.isFullyDownloaded = other.isFullyDownloaded;
        this.isStopped = other.isStopped;
    }

    @Override
    public int hashCode() {
        return id * 31;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (this.getClass() != obj.getClass()) {
            return false;
        }

        return this.id == ((SavedThread) obj).id;
    }
}
