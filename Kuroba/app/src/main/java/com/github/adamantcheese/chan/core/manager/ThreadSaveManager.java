package com.github.adamantcheese.chan.core.manager;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.mapper.ThreadMapper;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.schedulers.Schedulers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ThreadSaveManager {
    private static final String TAG = "ThreadSaveManager";
    private static final int MAX_DOWNLOAD_IMAGE_WAITING_TIME_SECONDS = 20;

    private Gson gson;

    private OkHttpClient okHttpClient = new OkHttpClient()
            .newBuilder()
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    @Nullable
    private Disposable disposable;

    @Inject
    public ThreadSaveManager(Gson gson) {
        this.gson = gson;
    }

    public void saveThread(ChanThread thread, ThreadSaveManagerCallbacks callbacks) {
        if (thread == null) {
            callbacks.onError(new NullPointerException("ChanThread is null"));
            return;
        }

//        if (!permissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//            // TODO: check for permission and try again
//            callbacks.onNoWriteExternalStoragePermission();
//            return;
//        }

        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        }

        try {
            String subDirs = getThreadSubDir(thread);

            File threadSaveDir = new File(ChanSettings.saveLocation.get(), subDirs);
            if (!threadSaveDir.exists()) {
                if (!threadSaveDir.mkdirs()) {
                    throw new IOException("Could not create a directory to save the thread " +
                            "to (full path: " + threadSaveDir.getAbsolutePath() + ")");
                }
            }

            File threadSaveDirImages = new File(threadSaveDir, "images");
            if (!threadSaveDirImages.exists()) {
                if (!threadSaveDirImages.mkdirs()) {
                    throw new IOException("Could not create a directory to save the thread images" +
                            "to (full path: " + threadSaveDirImages.getAbsolutePath() + ")");
                }
            }

            disposable = Completable.fromRunnable(() -> saveThreadJsonToFile(thread, threadSaveDir))
                    // Execute json serialization on the executor
                    .subscribeOn(Schedulers.from(executorService))
                    .andThen(Completable.defer(() -> {
                        // for each thread create a new flowable
                        return Flowable.fromIterable(thread.posts)
                                // Here we create a separate reactive stream for each image request.
                                //                   |
                                //                 / | \
                                //                /  |  \
                                //               /   |   \
                                //               V   V   V // separate streams
                                //               |   |   |
                                //               o   o   o // download images in parallel
                                //               |   |   | // (availableProcessors count at a time)
                                //               |   |   |
                                //               V   V   V // combine them back to a single stream
                                //               \   |   /
                                //                \  |  /
                                //                 \ | /
                                //                   |
                                .flatMapCompletable((post) -> {
                                    return downloadImages(
                                            threadSaveDirImages,
                                            post,
                                            thread.loadable.board.spoilers);
                                });
                    }))
                    // Get the results on the main thread
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        Logger.d(TAG, "Thread download success!");
                        callbacks.onSuccess();
                    }, (error) -> {
                        Logger.d(TAG, "Thread download error");
                        deleteThread(thread);
                        callbacks.onError(error);
                    });

        } catch (Throwable error) {
            Logger.e(TAG, "Could not save thread", error);
            deleteThread(thread);

            callbacks.onError(error);
        }
    }

    private void saveThreadJsonToFile(ChanThread thread, File threadSaveDir) {
        try {
            SerializableThread serializableThread = ThreadMapper.toSerializableThread(thread);
            String threadJson = gson.toJson(serializableThread);

            File threadFile = new File(threadSaveDir, "thread");
            if (!threadFile.exists() && !threadFile.createNewFile()) {
                throw new IOException("Could not create the thread file (path: " + threadFile.getAbsolutePath() + ")");
            }

            try (RandomAccessFile raf = new RandomAccessFile(threadFile, "rw")) {
                byte[] bytes = threadJson.getBytes(StandardCharsets.UTF_8);
                raf.writeInt(bytes.length);
                raf.write(bytes);
            }
        } catch (Throwable error) {
            Exceptions.propagate(error);
        }
    }

    private Completable downloadImages(
            File threadSaveDirImages,
            Post post,
            boolean boardHasSpoilers) {
        return Flowable.fromIterable(post.images)
                .flatMapCompletable((postImage) -> {
                    // Download each image in parallel on the executorService
                    return Completable.fromRunnable(() -> {
                        try {
                            downloadImageIntoFile(
                                    threadSaveDirImages,
                                    boardHasSpoilers,
                                    postImage.filename,
                                    postImage.extension,
                                    postImage.imageUrl,
                                    postImage.spoilerThumbnailUrl,
                                    postImage.thumbnailUrl);
                        } catch (IOException e) {
                            Exceptions.propagate(e);
                        }
                    })
                    // We don't really want to use more than "CPU processors count" threads here, but
                    // we want for every request to run on a separate thread in parallel
                    .subscribeOn(Schedulers.from(executorService))
                    .timeout(MAX_DOWNLOAD_IMAGE_WAITING_TIME_SECONDS, TimeUnit.SECONDS)
                    // Do nothing if an error occurs (like timeout exception) because we don't want
                    // to lose what we have already downloaded
                    .onErrorComplete();
                });
    }

    private void downloadImageIntoFile(
            File threadSaveDirImages,
            boolean boardHasSpoilers,
            String filename,
            String extension,
            HttpUrl imageUrl,
            HttpUrl spoilerThumbnailUrl,
            HttpUrl thumbnailUrl) throws IOException {
        Logger.d(TAG, "Downloading image with filename " + filename +
                " in a thread " + Thread.currentThread().getName());

        downloadImage(
                threadSaveDirImages,
                filename + "_" + "original" + "." + extension,
                imageUrl);
        downloadImage(
                threadSaveDirImages,
                filename + "_" + "thumbnail" + "." + extension,
                thumbnailUrl);

        if (boardHasSpoilers) {
            // TODO: do not download spoiler image for every image in a thread, do it once per thread at least
            downloadImage(
                    threadSaveDirImages,
                    filename + "_" + "spoiler" + "." + extension,
                    spoilerThumbnailUrl);
        }
    }

    private void downloadImage(File threadSaveDirImages, String filename, HttpUrl imageUrl) throws IOException {
        File imageFile = new File(threadSaveDirImages, filename);
        if (!imageFile.exists()) {
            Request request = new Request.Builder().url(imageUrl).build();

            // download the original image
            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.code() != 200) {
                    Logger.d(TAG, "Download image request returned bad status code: " + response.code());
                    return;
                }

                storeImageToFile(imageFile, response);
            }
        } else {
            Logger.d(TAG, "image " + imageFile.getAbsolutePath() + " already exists on the disk, skip it");
        }
    }

    private void storeImageToFile(
            File imageFile,
            Response response) throws IOException {
        if (!imageFile.createNewFile()) {
            throw new IOException("Could not create a file to save an image (path: " + imageFile.getAbsolutePath() + ")");
        }

        try (ResponseBody body = response.body()) {
            if (body == null) {
                throw new NullPointerException("Response body is null");
            }

            try (InputStream is = body.byteStream()) {
                try (OutputStream os = new FileOutputStream(imageFile)) {
                    IOUtils.copy(is, os);
                }
            }
        }
    }

    private void deleteThread(ChanThread thread) {
        String subDirs = getThreadSubDir(thread);

        File threadSaveDir = new File(ChanSettings.saveLocation.get(), subDirs);
        if (!threadSaveDir.exists()) {
            return;
        }

        IOUtils.deleteDirWithContents(threadSaveDir.getParentFile());
    }

    private String getThreadSubDir(ChanThread thread) {
        // saved_threads/4chan/g/11223344

        return "saved_threads" +
                File.separator +
                thread.loadable.site.name() +
                File.separator +
                thread.loadable.boardCode +
                File.separator + thread.op.no;
    }

    public interface ThreadSaveManagerCallbacks {
        void onSuccess();

        void onError(Throwable error);
    }
}
