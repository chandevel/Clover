package com.github.adamantcheese.chan.core.repository

import com.github.adamantcheese.chan.core.mapper.ThreadMapper
import com.github.adamantcheese.chan.core.model.Post
import com.github.adamantcheese.chan.core.model.save.SerializableThread
import com.github.adamantcheese.chan.core.saf.file.AbstractFile
import com.github.adamantcheese.chan.core.saf.file.ExternalFile
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.github.adamantcheese.chan.utils.Logger
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
constructor(private val gson: Gson) {

    @Throws(IOException::class)
    fun loadOldThreadFromJsonFile(
            threadSaveDir: AbstractFile): SerializableThread? {
        if (BackgroundUtils.isMainThread()) {
            throw RuntimeException("Cannot be executed on the main thread!")
        }

        val threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(THREAD_FILE_NAME)

        if (!threadFile.exists()) {
            Logger.d(TAG, "threadFile does not exist, threadFilePath = " + threadFile.getFullPath())
            return null
        }

        return threadFile.getInputStream()?.use { inputStream ->
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
            CouldNotGetParcelFileDescriptor::class)
    fun savePostsToJsonFile(
            oldSerializableThread: SerializableThread?,
            posts: List<Post>,
            threadSaveDir: AbstractFile) {
        if (BackgroundUtils.isMainThread()) {
            throw RuntimeException("Cannot be executed on the main thread!")
        }

        val threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(THREAD_FILE_NAME)

        if (!threadFile.exists() && !threadFile.create()) {
            throw CouldNotCreateThreadFile(threadFile)
        }

        threadFile.getOutputStream()?.use { outputStream ->
            // Update the thread file
            return@use DataOutputStream(outputStream).use { dos ->
                val serializableThread = if (oldSerializableThread != null) {
                    // Merge with old posts if there are any
                    oldSerializableThread.merge(posts)
                } else {
                    // Use only the new posts
                    ThreadMapper.toSerializableThread(posts)
                }

                val bytes = gson.toJson(serializableThread)
                        .toByteArray(StandardCharsets.UTF_8)

                dos.write(bytes)
                dos.flush()

                return@use Unit
            }
        } ?: throw IOException("threadFile.getOutputStream() returned null, threadFile = "
                + threadFile.getFullPath())
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
