package org.floens.chan.utils;

import android.util.Log;

import java.io.File;

public class FileCache {
    private static final String TAG = "FileCache";

    private final File directory;
    private final long maxSize;

    private long size;

    public FileCache(File directory, long maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;

        if (!directory.exists() && !directory.mkdirs()) {
            Logger.e(TAG, "Unable to create file cache dir " + directory.getAbsolutePath());
        }

        calculateSize();
    }

    public File get(String key) {
        return new File(directory, Integer.toString(key.hashCode()));
    }

    public void put(File file) {
        size += file.length();

        trim();
    }

    public boolean delete(File file) {
        size -= file.length();

        return file.delete();
    }

    private void trim() {
        int tries = 0;
        while (size > maxSize && tries++ < 10) {
            File[] files = directory.listFiles();
            if (files == null) {
                break;
            }
            long age = Long.MAX_VALUE;
            long last;
            File oldest = null;
            for (File file : files) {
                last = file.lastModified();
                if (last < age && last != 0L) {
                    age = last;
                    oldest = file;
                }
            }

            if (oldest == null) {
                Log.e(TAG, "No files to trim");
                break;
            } else {
                Log.d(TAG, "Deleting " + oldest.getAbsolutePath());
                if (!delete(oldest)) {
                    Log.e(TAG, "Cannot delete cache file");
                    calculateSize();
                    break;
                }
            }

            calculateSize();
        }
    }

    private void calculateSize() {
        size = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
    }
}
