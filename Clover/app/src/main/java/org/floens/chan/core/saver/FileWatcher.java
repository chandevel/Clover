/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.saver;

import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileWatcher {
    private static final String TAG = "FileWatcher";

    private static final Comparator<FileItem> FILE_COMPARATOR = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem a, FileItem b) {
            return a.file.getName().compareToIgnoreCase(b.file.getName());
        }
    };

    private final FileWatcherCallback callback;
    boolean initialized = false;
    private File startingPath;

    private File currentPath;

    private AFileObserver fileObserver;

    public FileWatcher(FileWatcherCallback callback, File startingPath) {
        this.callback = callback;
        this.startingPath = startingPath;
    }

    public void initialize() {
        initialized = true;

        if (!StorageHelper.canNavigate(startingPath)) {
            startingPath = Environment.getExternalStorageDirectory();
        }

        navigateTo(startingPath);
    }

    public File getCurrentPath() {
        return currentPath;
    }

    public void navigateUp() {
        File parentFile = currentPath.getParentFile();
        if (parentFile != null && StorageHelper.canNavigate(parentFile)) {
            navigateTo(parentFile);
        }
    }

    public void navigateTo(File to) {
        if (!StorageHelper.canNavigate(to)) {
            throw new IllegalArgumentException("Cannot navigate to " + to.getAbsolutePath());
        }

        if (fileObserver != null) {
            fileObserver.stopWatching();
            fileObserver = null;
        }

        // TODO: fileobserver is broken
//        int mask = FileObserver.CREATE | FileObserver.DELETE;
//        fileObserver = new AFileObserver(to.getAbsolutePath(), mask);
//        fileObserver = new AFileObserver("/sdcard/");
//        fileObserver.startWatching();

        currentPath = to;

        File[] files = currentPath.listFiles();

        List<FileItem> folderList = new ArrayList<>();
        List<FileItem> fileList = new ArrayList<>();
        for (File file : files) {
            if (StorageHelper.canNavigate(file)) {
                folderList.add(new FileItem(file));
            } else if (file.isFile()) {
                fileList.add(new FileItem(file));
            }
        }
        Collections.sort(folderList, FILE_COMPARATOR);
        Collections.sort(fileList, FILE_COMPARATOR);
        List<FileItem> items = new ArrayList<>(folderList.size() + fileList.size());
        items.addAll(folderList);
        items.addAll(fileList);

        boolean canNavigateUp = StorageHelper.canNavigate(currentPath.getParentFile());

        callback.onFiles(new FileItems(currentPath, items, canNavigateUp));
    }

    private class AFileObserver extends FileObserver {
        public AFileObserver(File path) {
            super(path);
        }

        public AFileObserver(File path, int mask) {
            super(path, mask);
        }

        @Override
        public void onEvent(int event, String path) {
            Log.d(TAG, "onEvent() called with: " + "event = [" + event + "], path = [" + path + "]");
        }
    }

    public interface FileWatcherCallback {
        void onFiles(FileItems fileItems);
    }

    public static class FileItem {
        public File file;

        public FileItem(File file) {
            this.file = file;
        }

        public boolean isFile() {
            return file.isFile();
        }

        public boolean isFolder() {
            return file.isDirectory();
        }

        public boolean canNavigate() {
            return StorageHelper.canNavigate(file);
        }

        public boolean canOpen() {
            return StorageHelper.canOpen(file);
        }
    }

    public static class FileItems {
        public final File path;
        public final List<FileItem> fileItems;

        public final boolean canNavigateUp;

        public FileItems(File path, List<FileItem> fileItems, boolean canNavigateUp) {
            this.path = path;
            this.fileItems = fileItems;
            this.canNavigateUp = canNavigateUp;
        }
    }
}
