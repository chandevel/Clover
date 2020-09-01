package com.github.adamantcheese.chan.core.database;

import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseSavedThreadManager {
    DatabaseHelper helper;
    FileManager fileManager;

    public DatabaseSavedThreadManager(DatabaseHelper helper, FileManager fileManager) {
        this.helper = helper;
        this.fileManager = fileManager;
    }

    public Callable<Long> countDownloadingThreads() {
        return () -> helper.getSavedThreadDao()
                .queryBuilder()
                .where()
                .eq(SavedThread.IS_STOPPED, false)
                .and()
                .eq(SavedThread.IS_FULLY_DOWNLOADED, false)
                .countOf();
    }

    public Callable<List<SavedThread>> getSavedThreads() {
        return () -> {
            // We don't need fully downloaded threads here
            return helper.getSavedThreadDao().queryBuilder().where().eq(SavedThread.IS_FULLY_DOWNLOADED, false).query();
        };
    }

    public Callable<SavedThread> startSavingThread(final SavedThread savedThread) {
        return () -> {
            SavedThread prevSavedThread = getSavedThreadByLoadableId(savedThread.loadableId).call();
            if (prevSavedThread != null) {
                helper.getSavedThreadDao().update(merge(prevSavedThread, savedThread));
                return savedThread;
            }

            helper.getSavedThreadDao().create(savedThread);
            return savedThread;
        };
    }

    private SavedThread merge(SavedThread prevSavedThread, SavedThread savedThread) {
        if (prevSavedThread.loadableId != savedThread.loadableId) {
            throw new RuntimeException(
                    "Cannot merge threads with different loadableIds (prevLoadableId = " + prevSavedThread.loadableId
                            + ", currLoadableId = " + savedThread.loadableId + ")");
        }

        return new SavedThread(
                prevSavedThread.id,
                savedThread.isFullyDownloaded,
                savedThread.isStopped,
                Math.max(prevSavedThread.lastSavedPostNo, savedThread.lastSavedPostNo),
                prevSavedThread.loadableId
        );
    }

    public Callable<Void> updateLastSavedPostNo(int loadableId, int lastPostNo) {
        return () -> {
            SavedThread savedThread = helper.getSavedThreadDao()
                    .queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return null;
            }

            savedThread.lastSavedPostNo = lastPostNo;
            helper.getSavedThreadDao().update(savedThread);

            return null;
        };
    }

    public Callable<Boolean> updateThreadStoppedFlagByLoadableId(int loadableId, boolean stop) {
        return () -> {
            SavedThread savedThread = helper.getSavedThreadDao()
                    .queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return false;
            }

            if (savedThread.isFullyDownloaded) {
                return false;
            }

            savedThread.isStopped = stop;
            helper.getSavedThreadDao().update(savedThread);

            return true;
        };
    }

    public Callable<Boolean> updateThreadFullyDownloadedByLoadableId(int loadableId) {
        return () -> {
            SavedThread savedThread = helper.getSavedThreadDao()
                    .queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return false;
            }

            if (savedThread.isFullyDownloaded) {
                return true;
            }

            savedThread.isStopped = true;
            savedThread.isFullyDownloaded = true;
            helper.getSavedThreadDao().update(savedThread);

            return false;
        };
    }

    public Callable<Integer> getLastSavedPostNo(int loadableId) {
        return () -> {
            SavedThread savedThread = helper.getSavedThreadDao()
                    .queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return 0;
            }

            return savedThread.lastSavedPostNo;
        };
    }

    public Callable<SavedThread> getSavedThreadByLoadableId(int loadableId) {
        return () -> helper.getSavedThreadDao()
                .queryBuilder()
                .where()
                .eq(SavedThread.LOADABLE_ID, loadableId)
                .queryForFirst();
    }

    public Callable<Void> deleteSavedThread(Loadable loadable) {
        return () -> {
            SavedThread prevSavedThread = getSavedThreadByLoadableId(loadable.id).call();
            if (prevSavedThread == null) {
                return null;
            }

            DeleteBuilder<SavedThread, Integer> db = helper.getSavedThreadDao().deleteBuilder();
            db.where().eq(SavedThread.LOADABLE_ID, loadable.id);
            db.delete();

            deleteThreadFromDisk(loadable);
            return null;
        };
    }

    public void deleteThreadFromDisk(Loadable loadable) {
        AbstractFile localThreadsDir = fileManager.newBaseDirectoryFile(LocalThreadsBaseDirectory.class);

        if (localThreadsDir == null || !fileManager.exists(localThreadsDir)
                || !fileManager.isDirectory(localThreadsDir)) {
            // Probably already deleted
            return;
        }

        AbstractFile threadDir = localThreadsDir.clone(ThreadSaveManager.getThreadSubDir(loadable));

        if (!fileManager.exists(threadDir) || !fileManager.isDirectory(threadDir)) {
            // Probably already deleted
            return;
        }

        if (!fileManager.delete(threadDir)) {
            Logger.d(this, "deleteThreadFromDisk() Could not delete SAF directory " + threadDir.getFullPath());
        }
    }

    public Callable<Void> deleteSavedThreads(List<Loadable> loadableList) {
        return () -> {
            for (Loadable loadable : loadableList) {
                deleteSavedThread(loadable).call();
            }

            return null;
        };
    }

    public Callable<Void> deleteAllSavedThreads() {
        return () -> {
            DeleteBuilder<SavedThread, Integer> db = helper.getSavedThreadDao().deleteBuilder();
            db.delete();

            return null;
        };
    }
}
