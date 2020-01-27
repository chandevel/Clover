package com.github.adamantcheese.chan.core.repository

import com.github.adamantcheese.chan.core.mapper.ThreadMapper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.save.SerializableThread
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.fsaf.file.AbstractFile
import com.github.k1rakishou.fsaf.file.ExternalFile
import com.github.k1rakishou.fsaf.file.FileSegment
import com.google.gson.Gson
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.inject.Inject

class SavedThreadLoaderRepository
/**
 * It would probably be a better idea to save posts in the database but then users won't be
 * able to backup them and they would be deleted after every app uninstall. This implementation
 * is slower than the DB one, but at least users will have their threads even after app
 * uninstall/app data clearing.
 */
@Inject
constructor(
        private val gson: Gson,
        private val fileManager: FileManager
) {

    @Throws(IOException::class)
    fun loadOldThreadFromJsonFile(
            threadSaveDir: AbstractFile
    ): SerializableThread? {
        BackgroundUtils.ensureBackgroundThread()

        val threadFile = threadSaveDir.clone(FileSegment(THREAD_FILE_NAME))

        if (!fileManager.exists(threadFile)) {
            Logger.d(TAG, "threadFile does not exist, threadFilePath = " + threadFile.getFullPath())
            return null
        }

        return fileManager.getInputStream(threadFile)?.use { inputStream ->
            return@use DataInputStream(inputStream).use { dis ->
                val json = String(dis.readBytes(), StandardCharsets.UTF_8)

                return@use gson.fromJson<SerializableThread>(
                        json,
                        SerializableThread::class.java)
            }
        }
    }

    @Throws(IOException::class,
            CouldNotCreateThreadFile::class,
            CouldNotGetParcelFileDescriptor::class
    )
    fun savePostsToJsonFile(
            oldSerializableThread: SerializableThread?,
            posts: List<Post>,
            threadSaveDir: AbstractFile
    ) {
        BackgroundUtils.ensureBackgroundThread()

        val threadFile = threadSaveDir.clone(FileSegment(THREAD_FILE_NAME))
        val createdThreadFile = fileManager.create(threadFile)

        if (!fileManager.exists(threadFile) || createdThreadFile == null) {
            throw CouldNotCreateThreadFile(threadFile)
        }

        fileManager.getOutputStream(createdThreadFile)?.use { outputStream ->
            // Update the thread file
            return@use DataOutputStream(outputStream).use { dos ->
                val serializableThread = if (oldSerializableThread != null) {
                    // Merge with old posts if there are any
                    oldSerializableThread.merge(posts)
                } else {
                    // Use only the new posts
                    ThreadMapper.toSerializableThread(posts)
                }

                val bytes = gson.toJson(serializableThread).toByteArray(StandardCharsets.UTF_8)

                dos.write(bytes)
                dos.flush()

                return@use
            }
        } ?: throw IOException("threadFile.getOutputStream() returned null, threadFile = "
                + createdThreadFile.getFullPath())
    }

    inner class CouldNotGetParcelFileDescriptor(threadFile: ExternalFile)
        : Exception("getParcelFileDescriptor() returned null, threadFilePath = "
            + threadFile.getFullPath())

    inner class CouldNotCreateThreadFile(threadFile: AbstractFile)
        : Exception("Could not create the thread file (path: " + threadFile.getFullPath() + ")")

    companion object {
        private const val TAG = "SavedThreadLoaderRepository"
        const val THREAD_FILE_NAME = "thread.json"
    }
}
