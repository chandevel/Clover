package com.github.adamantcheese.chan.core.repository;

import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.mapper.ThreadMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.core.saf.file.ExternalFile;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

public class SavedThreadLoaderRepository {
    private static final String TAG = "SavedThreadLoaderRepository";
    private static final int MAX_THREAD_SIZE_BYTES = 50 * 1024 * 1024; // 50mb
    private static final int THREAD_FILE_HEADER_SIZE = 4;
    public static final String THREAD_FILE_NAME = "thread.json";

    private Gson gson;

    /**
     * It would probably be a better idea to save posts in the database but then users won't be
     * able to backup them and they would be deleted after every app uninstall. This implementation
     * is slower than the DB one, but at least users will have their threads even after app
     * uninstall/app data clearing.
     * */
    @Inject
    public SavedThreadLoaderRepository(Gson gson) {
        this.gson = gson;
    }

    @Nullable
    public SerializableThread loadOldThreadFromJsonFile(
            ExternalFile threadSaveDir) throws IOException, OldThreadTakesTooMuchSpace {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Cannot be executed on the main thread!");
        }

        ExternalFile threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(THREAD_FILE_NAME);
        if (!threadFile.exists()) {
            Logger.d(TAG, "threadFile does not exist, threadFilePath = " + threadFile.getFullPath());
            return null;
        }

        try (ParcelFileDescriptor parcelFileDescriptor = threadFile.getParcelFileDescriptor(
                ExternalFile.FileDescriptorMode.Read)) {
            if (parcelFileDescriptor == null) {
                Logger.d(TAG, "getParcelFileDescriptor() returned null, threadFilePath = "
                        + threadFile.getFullPath());
                return null;
            }

            try (FileReader fileReader = new FileReader(parcelFileDescriptor.getFileDescriptor())) {
                int fileLength = getThreadFileLength(fileReader);
                if (fileLength <= 0 || fileLength > MAX_THREAD_SIZE_BYTES) {
                    throw new OldThreadTakesTooMuchSpace(fileLength);
                }

                long skipped = fileReader.skip(THREAD_FILE_HEADER_SIZE);
                if (skipped != THREAD_FILE_HEADER_SIZE) {
                    throw new IOException("Could not skip " + THREAD_FILE_HEADER_SIZE + " bytes");
                }

                return gson.fromJson(fileReader, SerializableThread.class);
            }
        }
    }

    public void savePostsToJsonFile(
            @Nullable SerializableThread oldSerializableThread,
            List<Post> posts,
            ExternalFile threadSaveDir
    ) throws IOException, CouldNotCreateThreadFile, CouldNotGetParcelFileDescriptor {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Cannot be executed on the main thread!");
        }

        ExternalFile threadFile = threadSaveDir
                .clone()
                .appendFileNameSegment(THREAD_FILE_NAME);

        if (!threadFile.exists() && !threadFile.create()) {
            throw new CouldNotCreateThreadFile(threadFile);
        }

        try (ParcelFileDescriptor parcelFileDescriptor = threadFile.getParcelFileDescriptor(
                ExternalFile.FileDescriptorMode.WriteTruncate)) {
            if (parcelFileDescriptor == null) {
                throw new CouldNotGetParcelFileDescriptor(threadFile);
            }


            // Update the thread file
            try (FileWriter fileWriter = new FileWriter(parcelFileDescriptor.getFileDescriptor())) {
                SerializableThread serializableThread;

                if (oldSerializableThread != null) {
                    // Merge with old posts if there are any
                    serializableThread = oldSerializableThread.merge(posts);
                } else {
                    // Use only the new posts
                    serializableThread = ThreadMapper.toSerializableThread(posts);
                }

                char[] threadJsonBytes = gson.toJson(serializableThread).toCharArray();
                char[] lengthChars = String.valueOf(threadJsonBytes.length).toCharArray();

                // TODO: may not work!
                fileWriter.write(lengthChars);
                fileWriter.write(threadJsonBytes);
            }
        }
    }

    private int getThreadFileLength(FileReader fileReader) throws IOException {
        char[] sizeBytes = new char[THREAD_FILE_HEADER_SIZE];
        int readCount = fileReader.read(sizeBytes);

        if (readCount != THREAD_FILE_HEADER_SIZE) {
            throw new IOException("Could not read the length of the thread from the thread file header");
        }

        String sizeBytesString = String.valueOf(sizeBytes);

        try {
            return Integer.parseInt(sizeBytesString);
        } catch (NumberFormatException nfe) {
            // Convert the NumberFormatException into an IOException
            throw new IOException("Couldn't convert file size string into an int, sizeBytesString = "
                    + sizeBytesString);
        }
    }

    public class CouldNotGetParcelFileDescriptor extends Exception {
        public CouldNotGetParcelFileDescriptor(ExternalFile threadFile) {
            super("getParcelFileDescriptor() returned null, threadFilePath = "
                    + threadFile.getFullPath());
        }
    }

    public class OldThreadTakesTooMuchSpace extends Exception {
        public OldThreadTakesTooMuchSpace(int size) {
            super("Old serialized thread takes way too much space: " + size +
                    " bytes. You are not trying to save an infinite or sticky thread, right? " +
                    "It's not supported.");
        }
    }

    public class CouldNotCreateThreadFile extends Exception {
        public CouldNotCreateThreadFile(ExternalFile threadFile) {
            super("Could not create the thread file " +
                    "(path: " + threadFile.getFullPath() + ")");
        }
    }
}
