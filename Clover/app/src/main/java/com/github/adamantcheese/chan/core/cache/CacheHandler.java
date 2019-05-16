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
package com.github.adamantcheese.chan.core.cache;

import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;
import android.util.Pair;

import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CacheHandler {
    private static final String TAG = "CacheHandler";
    private static final int TRIM_TRIES = 20;

    private final ExecutorService pool = Executors.newFixedThreadPool(1);

    private final File directory;
    private final long maxSize;

    /**
     * An estimation of the current size of the directory. Used to check if trim must be run
     * because the folder exceeds the maximum size.
     */
    private AtomicLong size = new AtomicLong();
    private AtomicBoolean trimRunning = new AtomicBoolean(false);

    public CacheHandler(File directory, long maxSize) {
        this.directory = directory;
        this.maxSize = maxSize;

        createDirectories();
        backgroundRecalculateSize();
    }

    @MainThread
    public boolean exists(String key) {
        return get(key).exists();
    }

    @MainThread
    public File get(String key) {
        createDirectories();

        return new File(directory, hash(key));
    }

    @MainThread
    protected void fileWasAdded(File file) {
        long adjustedSize = size.addAndGet(file.length());

        if (adjustedSize > maxSize && trimRunning.compareAndSet(false, true)) {
            pool.submit(() -> {
                try {
                    trim();
                } catch (Exception e) {
                    Logger.e(TAG, "Error trimming", e);
                } finally {
                    trimRunning.set(false);
                }
            });
        }
    }

    @MainThread
    public void clearCache() {
        Logger.d(TAG, "Clearing cache");

        if (directory.exists() && directory.isDirectory()) {
            for (File file : directory.listFiles()) {
                if (!file.delete()) {
                    Logger.d(TAG, "Could not delete cache file while clearing cache " +
                            file.getName());
                }
            }
        }

        recalculateSize();
    }

    @MainThread
    public void createDirectories() {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Logger.e(TAG, "Unable to create file cache dir " +
                        directory.getAbsolutePath());
            }
        }
    }

    @MainThread
    private void backgroundRecalculateSize() {
        pool.submit(this::recalculateSize);
    }

    @AnyThread
    private void recalculateSize() {
        long calculatedSize = 0;

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                calculatedSize += file.length();
            }
        }

        size.set(calculatedSize);
    }

    @WorkerThread
    private void trim() {
        File[] directoryFiles = directory.listFiles();

        // Don't try to trim empty directories or just one file in it.
        if (directoryFiles == null || directoryFiles.length <= 1) {
            return;
        }

        // Get all files with their last modified times.
        List<Pair<File, Long>> files = new ArrayList<>(directoryFiles.length);
        for (File file : directoryFiles) {
            files.add(new Pair<>(file, file.lastModified()));
        }

        // Sort by oldest first.
        Collections.sort(files, (o1, o2) -> Long.signum(o1.second - o2.second));

        // Trim as long as the directory size exceeds the threshold and we haven't reached
        // the trim limit.
        long workingSize = size.get();
        for (int i = 0; workingSize >= maxSize && i < Math.min(files.size(), TRIM_TRIES); i++) {
            File file = files.get(i).first;

            Logger.d(TAG, "Delete for trim " + file.getAbsolutePath());
            workingSize -= file.length();

            boolean deleteResult = file.delete();

            if (!deleteResult) {
                Logger.e(TAG, "Failed to delete cache file for trim");
            }
        }

        recalculateSize();
    }

    @AnyThread
    private String hash(String key) {
        return String.valueOf(key.hashCode());
    }
}
