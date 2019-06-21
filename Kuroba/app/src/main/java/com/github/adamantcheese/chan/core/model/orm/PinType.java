package com.github.adamantcheese.chan.core.model.orm;

public enum PinType {
    WatchNewPosts(Pin.WATCH_NEW_POSTS),
    DownloadNewPosts(Pin.DOWNLOAD_NEW_POSTS),
    WatchAndDownload(Pin.WATCH_NEW_POSTS | Pin.DOWNLOAD_NEW_POSTS);

    private int typeValue;

    PinType(int typeValue) {
        this.typeValue = typeValue;
    }

    public int getTypeValue() {
        return typeValue;
    }

    public boolean hasDownloadFlag() {
        return (typeValue & DownloadNewPosts.typeValue) != 0;
    }

    public boolean hasNoFlags() {
        return typeValue == 0;
    }

    public void removeDownloadNewPostsFlag() {
        if ((typeValue & DownloadNewPosts.typeValue) != 0) {
            typeValue &= ~(DownloadNewPosts.typeValue);
        }
    }

    public void addDownloadNewPostsFlag() {
        if ((typeValue & DownloadNewPosts.typeValue) == 0) {
            typeValue |= DownloadNewPosts.typeValue;
        }
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