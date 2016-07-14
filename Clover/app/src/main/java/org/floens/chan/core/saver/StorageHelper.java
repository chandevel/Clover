package org.floens.chan.core.saver;

import java.io.File;

public class StorageHelper {
    private static final String TAG = "StorageHelper";

    public static boolean canNavigate(File file) {
        return file != null && !isDirectoryBlacklisted(file) && file.exists()
                && file.isDirectory() && file.canRead();
    }

    public static boolean isDirectoryBlacklisted(File file) {
        String absolutePath = file.getAbsolutePath();
        switch (absolutePath) {
            case "/storage":
                return true;
            case "/storage/emulated":
                return true;
            case "/storage/emulated/0/0":
                return true;
            case "/storage/emulated/legacy":
                return true;
        }
        return false;
    }

    public static boolean canOpen(File file) {
        return file != null && file.exists() && file.isFile() && file.canRead();
    }
}
