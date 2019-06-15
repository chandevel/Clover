package com.github.adamantcheese.chan.core.database;

import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.j256.ormlite.stmt.DeleteBuilder;

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
            // TODO: check duplicates first

            helper.savedThreadDao.create(savedThread);
            return savedThread;
        };
    }

    public Callable<Boolean> stopSavingThread(int pinId) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.PIN_ID, pinId)
                    .queryForFirst();
            if (savedThread == null) {
                return false;
            }

            if (savedThread.isFullyDownloaded) {
                return false;
            }

            savedThread.isStopped = true;
            helper.savedThreadDao.update(savedThread);

            return false;
        };
    }

    public Callable<Void> updateLastSavedPostNo(int pinId, int lastPostNo) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.PIN_ID, pinId)
                    .queryForFirst();
            if (savedThread == null) {
                return null;
            }

            savedThread.lastSavedPostNo = lastPostNo;
            helper.savedThreadDao.update(savedThread);

            return null;
        };
    }


    public Callable<Void> updateThread(SavedThread savedThread) {
        return () -> {
            helper.savedThreadDao.update(savedThread);
            return null;
        };
    }

    public Callable<Void> deleteSavedThread(int pinId) {
        return () -> {
            DeleteBuilder<SavedThread, Integer> deleteBuilder = helper.savedThreadDao.deleteBuilder();
            deleteBuilder.where().in(SavedThread.PIN_ID, pinId);
            deleteBuilder.delete();

            return null;
        };
    }

    public Callable<Integer> getLastSavedPostNo(int pinId) {
        return () -> {
            SavedThread savedThread = helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.PIN_ID, pinId)
                    .queryForFirst();
            if (savedThread == null) {
                return 0;
            }

            return savedThread.lastSavedPostNo;
        };
    }

    public Callable<SavedThread> getSavedThreadByPinId(int pinId) {
        return () -> {
            return helper.savedThreadDao.queryBuilder()
                    .where()
                    .eq(SavedThread.PIN_ID, pinId)
                    .queryForFirst();
        };
    }
}
