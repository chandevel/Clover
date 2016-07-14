package org.floens.chan.core.saver;

import android.os.FileObserver;
import android.util.Log;

import org.floens.chan.core.model.FileItem;
import org.floens.chan.core.model.FileItems;

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
        public AFileObserver(String path) {
            super(path);
        }

        public AFileObserver(String path, int mask) {
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
}
