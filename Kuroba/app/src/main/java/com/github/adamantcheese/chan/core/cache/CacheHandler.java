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

import android.util.Pair;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.WorkerThread;

import com.github.adamantcheese.chan.core.saf.file.AbstractFile;
import com.github.adamantcheese.chan.core.saf.file.RawFile;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CacheHandler {
    private static final String TAG = "CacheHandler";
    //1GB for prefetching, so that entire threads can be loaded at once more easily, otherwise 100MB is plenty
    private static final long FILE_CACHE_DISK_SIZE = (ChanSettings.autoLoadThreadImages.get() ? 1000 : 100) * 1024 * 1024;
    private static final String CACHE_EXTENSION = "cache";

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final RawFile cacheDirFile;

    /**
     * An estimation of the current size of the directory. Used to check if trim must be run
     * because the folder exceeds the maximum size.
     */
    private AtomicLong size = new AtomicLong();
    private AtomicBoolean trimRunning = new AtomicBoolean(false);

    public CacheHandler(RawFile cacheDirFile) {
        this.cacheDirFile = cacheDirFile;

        createDirectories();
        backgroundRecalculateSize();
    }

    @MainThread
    public boolean exists(String key) {
        return get(key).exists();
    }

    @MainThread
    public RawFile get(String key) {
        createDirectories();

        String fileName = String.format(
                "%s.%s",
                // We need extension here because AbstractFile expects all file names to have
                // extensions
                String.valueOf(key.hashCode()), CACHE_EXTENSION);

        return cacheDirFile.clone()
                .appendFileNameSegment(fileName);
    }

    public File randomCacheFile() throws IOException {
        createDirectories();

        File cacheDir = new File(cacheDirFile.getFullPath());
        File newFile = new File(cacheDir, String.valueOf(System.nanoTime()));

        while (newFile.exists()) {
            newFile = new File(cacheDir, String.valueOf(System.nanoTime()));
        }

        if (!newFile.createNewFile()) {
            throw new IOException("Could not create new file in cache directory, newFile = "
                    + newFile.getAbsolutePath());
        }

        return newFile;
    }

    @MainThread
    public AtomicLong getSize() {
        return size;
    }

    @MainThread
    protected void fileWasAdded(long fileLen) {
        long adjustedSize = size.addAndGet(fileLen);

        if (adjustedSize > FILE_CACHE_DISK_SIZE && trimRunning.compareAndSet(false, true)) {
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

        if (cacheDirFile.exists() && cacheDirFile.isDirectory()) {
            for (AbstractFile file : cacheDirFile.listFiles()) {
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
        if (!cacheDirFile.exists() && !cacheDirFile.create()) {
            throw new RuntimeException("Unable to create file cache dir " + cacheDirFile.getFullPath());
        }
    }

    @MainThread
    private void backgroundRecalculateSize() {
        pool.submit(this::recalculateSize);
    }

    @AnyThread
    private void recalculateSize() {
        long calculatedSize = 0;

        List<RawFile> files = cacheDirFile.listFiles();
        for (RawFile file : files) {
            calculatedSize += file.getLength();
        }

        size.set(calculatedSize);
    }

    @WorkerThread
    private void trim() {
        List<RawFile> directoryFiles = cacheDirFile.listFiles();

        // Don't try to trim empty directories or just one file in it.
        if (directoryFiles.size() <= 1) {
            return;
        }

        // Get all files with their last modified times.
        List<Pair<RawFile, Long>> files = new ArrayList<>(directoryFiles.size());
        for (RawFile file : directoryFiles) {
            files.add(new Pair<>(file, file.lastModified()));
        }

        // Sort by oldest first.
        Collections.sort(files, (o1, o2) -> Long.signum(o1.second - o2.second));

        //Pre-trim based on time, trash anything older than 6 hours
        List<Pair<RawFile, Long>> removed = new ArrayList<>();
        for (Pair<RawFile, Long> fileLongPair : files) {
            if (fileLongPair.second + 6 * 60 * 60 * 1000 < System.currentTimeMillis()) {
                Logger.d(TAG, "Delete for trim " + fileLongPair.first.getFullPath());
                if (!fileLongPair.first.delete()) {
                    Logger.e(TAG, "Failed to delete cache file for trim");
                }
                removed.add(fileLongPair);
            } else break; //only because we sorted earlier
        }
        for (Pair<RawFile, Long> deleted : removed) {
            files.remove(deleted);
        }
        recalculateSize();

        // Trim as long as the directory size exceeds the threshold (note that oldest is still first)
        long workingSize = size.get();
        for (int i = 0; workingSize >= FILE_CACHE_DISK_SIZE; i++) {
            AbstractFile file = files.get(i).first;

            Logger.d(TAG, "Delete for trim " + file.getFullPath());
            workingSize -= file.getLength();

            if (!file.delete()) {
                Logger.e(TAG, "Failed to delete cache file for trim");
            }
        }

        recalculateSize();
    }
}
