package com.github.adamantcheese.chan.core.manager;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.mapper.ThreadMapper;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.saf.FileManager;
import com.github.adamantcheese.chan.core.saf.file.ExternalFile;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

public class SavedThreadLoaderManager {
    private final static String TAG = "SavedThreadLoaderManager";

    private final Gson gson;
    private final DatabaseManager databaseManager;
    private final SavedThreadLoaderRepository savedThreadLoaderRepository;
    private final FileManager fileManager;

    @Inject
    public SavedThreadLoaderManager(
            Gson gson,
            DatabaseManager databaseManager,
            SavedThreadLoaderRepository savedThreadLoaderRepository,
            FileManager fileManager) {
        this.gson = gson;
        this.databaseManager = databaseManager;
        this.savedThreadLoaderRepository = savedThreadLoaderRepository;
        this.fileManager = fileManager;
    }

    @Nullable
    public ChanThread loadSavedThread(Loadable loadable) {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Cannot be executed on the main thread!");
        }

        if (ChanSettings.localThreadsLocationUri.get().isEmpty()) {
            throw new IllegalStateException("Local threads location is not set!");
        }

        String threadSubDir = ThreadSaveManager.getThreadSubDir(loadable);
        Uri localThreadsLocationUri = Uri.parse(ChanSettings.localThreadsLocationUri.get());
        ExternalFile threadSaveDir = fileManager.fromUri(localThreadsLocationUri)
                .appendSubDirSegment(threadSubDir);

        if (!threadSaveDir.exists() || !threadSaveDir.isDirectory()) {
            Logger.e(TAG, "threadSaveDir does not exist or is not a directory: "
                    + "(path = " + threadSaveDir.getFullPath()
                    + ", exists = " + threadSaveDir.exists()
                    + ", isDir = " + threadSaveDir.isDirectory() + ")");
            return null;
        }

        ExternalFile threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(SavedThreadLoaderRepository.THREAD_FILE_NAME);

        if (!threadFile.exists() || !threadFile.isFile() || !threadFile.canRead()) {
            Logger.e(TAG, "threadFile does not exist or not a file or cannot be read: " +
                    "(path = " + threadFile.getFullPath()
                    + ", exists = " + threadFile.exists()
                    + ", isFile = " + threadFile.isFile()
                    + ", canRead = " + threadFile.canRead() + ")");
            return null;
        }

        ExternalFile threadSaveDirImages = threadSaveDir
                .clone()
                .appendSubDirSegment("images");

        if (!threadSaveDirImages.exists() || !threadSaveDirImages.isDirectory()) {
            Logger.e(TAG, "threadSaveDirImages does not exist or is not a directory: "
                    + "(path = " + threadSaveDirImages.getFullPath()
                    + ", exists = " + threadSaveDirImages.exists()
                    + ", isDir = " + threadSaveDirImages.isDirectory() + ")");
            return null;
        }

        try {
            SerializableThread serializableThread = savedThreadLoaderRepository
                    .loadOldThreadFromJsonFile(threadSaveDir);
            if (serializableThread == null) {
                Logger.e(TAG, "Could not load thread from json");
                return null;
            }

            return ThreadMapper.fromSerializedThread(loadable, serializableThread);
        } catch (IOException | SavedThreadLoaderRepository.OldThreadTakesTooMuchSpace e) {
            Logger.e(TAG, "Could not load saved thread", e);
            return null;
        }
    }

}
