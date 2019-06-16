package com.github.adamantcheese.chan.core.database;

import com.github.adamantcheese.chan.core.model.orm.SavedThread;

import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseSavedThreadManager {
    @Inject
    DatabaseHelper helper;

    public DatabaseSavedThreadManager() {
        inject(this);
    }

    public Callable<List<SavedThread>> getSavedThreads() {
        return () -> helper.savedThreadDao.queryForAll();
    }

    public Callable<SavedThread> startSavingThread(final SavedThread savedThread) {
        return () -> {
            SavedThread prevSavedThread = getSavedThreadByLoadableId(savedThread.loadableId).call();
            if (prevSavedThread != null) {
                helper.savedThreadDao.update(merge(prevSavedThread, savedThread));
                return savedThread;
            }

            helper.savedThreadDao.create(savedThread);
            return savedThread;
        };
    }

    private SavedThread merge(SavedThread prevSavedThread, SavedThread savedThread) {
        if (prevSavedThread.loadableId != savedThread.loadableId) {
            throw new RuntimeException("Cannot merge threads with different loadableIds " +
                    "(prevLoadableId = " + prevSavedThread.loadableId +
                    ", currLoadableId = " + savedThread.loadableId + ")");
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
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return null;
            }

            savedThread.lastSavedPostNo = lastPostNo;
            helper.savedThreadDao.update(savedThread);

            return null;
        };
    }

    public Callable<Boolean> updateThreadStoppedFlagByLoadableId(int loadableId, boolean stop) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
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
            helper.savedThreadDao.update(savedThread);

            return true;
        };
    }

    public Callable<Boolean> updateThreadFullyDownloadedByLoadableId(int loadableId) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
            if (savedThread == null) {
                return false;
            }

            if (savedThread.isFullyDownloaded) {
                return true;
            }

            savedThread.isFullyDownloaded = true;
            helper.savedThreadDao.update(savedThread);

            return false;
        };
    }

    public Callable<Integer> getLastSavedPostNo(int loadableId) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
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
        return () -> {
            return helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.LOADABLE_ID, loadableId)
                    .queryForFirst();
        };
    }
}
