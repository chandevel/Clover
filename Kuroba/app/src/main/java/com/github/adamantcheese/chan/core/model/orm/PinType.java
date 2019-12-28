package com.github.adamantcheese.chan.core.model.orm;

/**
 * FUCK ENUMS
 */
public class PinType {
    public static final int WATCH_NEW_POSTS = 1;
    public static final int DOWNLOAD_NEW_POSTS = 1 << 1;

    private PinType() {
    }

    public static boolean hasDownloadFlag(int typeValue) {
        return (typeValue & DOWNLOAD_NEW_POSTS) != 0;
    }

    public static boolean hasWatchNewPostsFlag(int typeValue) {
        return (typeValue & WATCH_NEW_POSTS) != 0;
    }

    public static boolean hasNoFlags(int typeValue) {
        return typeValue == 0;
    }

    public static boolean hasFlags(int typeValue) {
        return typeValue != 0;
    }

    public static int removeDownloadNewPostsFlag(int typeValue) {
        if ((typeValue & DOWNLOAD_NEW_POSTS) != 0) {
            return typeValue & ~(DOWNLOAD_NEW_POSTS);
        }

        return typeValue;
    }

    public static int addDownloadNewPostsFlag(int typeValue) {
        if ((typeValue & DOWNLOAD_NEW_POSTS) == 0) {
            return typeValue | DOWNLOAD_NEW_POSTS;
        }

        return typeValue;
    }

    public static int removeWatchNewPostsFlag(int typeValue) {
        if ((typeValue & WATCH_NEW_POSTS) != 0) {
            return typeValue & ~(WATCH_NEW_POSTS);
        }

        return typeValue;
    }

    public static int addWatchNewPostsFlag(int typeValue) {
        if ((typeValue & WATCH_NEW_POSTS) == 0) {
            return typeValue | WATCH_NEW_POSTS;
        }

        return typeValue;
    }
}
