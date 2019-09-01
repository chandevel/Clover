package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.core.mapper.ThreadMapper
import com.github.adamantcheese.chan.core.model.ChanThread
import com.github.adamantcheese.chan.core.model.orm.Loadable
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository
import com.github.adamantcheese.chan.core.saf.FileManager
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import java.io.IOException
import javax.inject.Inject

class SavedThreadLoaderManager @Inject
constructor(
        private val savedThreadLoaderRepository: SavedThreadLoaderRepository,
        private val fileManager: FileManager) {

    fun loadSavedThread(loadable: Loadable): ChanThread? {
        if (BackgroundUtils.isMainThread()) {
            throw RuntimeException("Cannot be executed on the main thread!")
        }

        val threadSubDir = ThreadSaveManager.getThreadSubDir(loadable)
        val baseDir = fileManager.newLocalThreadFile()
        if (baseDir == null) {
            Logger.e(TAG, "loadSavedThread() fileManager.newLocalThreadFile() returned null")
            return null
        }

        val threadSaveDir = baseDir.appendSubDirSegment(threadSubDir)
        if (!threadSaveDir.exists() || !threadSaveDir.isDirectory()) {
            Logger.e(TAG, "threadSaveDir does not exist or is not a directory: "
                    + "(path = " + threadSaveDir.getFullPath()
                    + ", exists = " + threadSaveDir.exists()
                    + ", isDir = " + threadSaveDir.isDirectory() + ")")
            return null
        }

        val threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(SavedThreadLoaderRepository.THREAD_FILE_NAME)

        if (!threadFile.exists() || !threadFile.isFile() || !threadFile.canRead()) {
            Logger.e(TAG, "threadFile does not exist or not a file or cannot be read: " +
                    "(path = " + threadFile.getFullPath()
                    + ", exists = " + threadFile.exists()
                    + ", isFile = " + threadFile.isFile()
                    + ", canRead = " + threadFile.canRead() + ")")
            return null
        }

        val threadSaveDirImages = threadSaveDir
                .clone()
                .appendSubDirSegment("images")

        if (!threadSaveDirImages.exists() || !threadSaveDirImages.isDirectory()) {
            Logger.e(TAG, "threadSaveDirImages does not exist or is not a directory: "
                    + "(path = " + threadSaveDirImages.getFullPath()
                    + ", exists = " + threadSaveDirImages.exists()
                    + ", isDir = " + threadSaveDirImages.isDirectory() + ")")
            return null
        }

        try {
            val serializableThread = savedThreadLoaderRepository
                    .loadOldThreadFromJsonFile(threadSaveDir)
            if (serializableThread == null) {
                Logger.e(TAG, "Could not load thread from json")
                return null
            }

            return ThreadMapper.fromSerializedThread(loadable, serializableThread)
        } catch (e: IOException) {
            Logger.e(TAG, "Could not load saved thread", e)
            return null
        }
    }

    companion object {
        private const val TAG = "SavedThreadLoaderManager"
    }

}
