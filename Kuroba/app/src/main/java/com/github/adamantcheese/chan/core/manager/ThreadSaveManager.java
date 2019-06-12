package com.github.adamantcheese.chan.core.manager;

import android.util.Pair;

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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int TIMEOUT_SECONDS = 30;

    private Gson gson;

    private OkHttpClient okHttpClient = new OkHttpClient()
            .newBuilder()
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    @Nullable
    private Disposable disposable;

    @Inject
    public ThreadSaveManager(Gson gson) {
        this.gson = gson;
    }

    public void cancel() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        }
    }

    public void saveThread(ChanThread thread, ThreadSaveManagerCallbacks callbacks) {
        cancel();

        if (thread == null) {
            callbacks.onThreadSaveError(new NullPointerException("ChanThread is null"));
            return;
        }

        if (thread.posts.isEmpty()) {
            callbacks.onThreadSaveSuccess();
            return;
        }

        try {
            String threadSubDir = getThreadSubDir(thread);

            File threadSaveDir = new File(ChanSettings.saveLocation.get(), threadSubDir);
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

            String boardSubDir = getBoardSubDir(thread);

            File boardSaveDir = new File(ChanSettings.saveLocation.get(), boardSubDir);
            if (!boardSaveDir.exists()) {
                if (!boardSaveDir.mkdirs()) {
                    throw new IOException("Could not create a directory to save the spoiler image " +
                            "to (full path: " + boardSaveDir.getAbsolutePath() + ")");
                }
            }

            final AtomicInteger downloadedFiles = new AtomicInteger(0);
            final int totalImagesCount = getImagesCount(thread.posts);

            // Pair<Extension, Url>
            Pair<String, HttpUrl> spoilerImageUrlAndExtension = getSpoilerImageUrlAndExtension(thread.posts);

            callbacks.onThreadSavingStarted();

            disposable = Completable.fromRunnable(() -> saveThreadJsonToFile(thread, threadSaveDir))
                    // Execute json serialization on the executor
                    .subscribeOn(Schedulers.from(executorService))
                    .andThen(Completable.fromRunnable(() -> downloadSpoilerImage(
                            thread,
                            boardSaveDir,
                            spoilerImageUrlAndExtension)))
                    .andThen(Completable.defer(() -> {
                        // OP image may be deleted by moderators and we may end up with no images at all
                        if (totalImagesCount <= 0) {
                            return Completable.complete();
                        }

                        // for each post create a new flowable
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
                                            downloadedFiles,
                                            totalImagesCount,
                                            callbacks);
                                });
                    }))
                    // Get the results on the main thread
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {
                        Logger.d(TAG, "Thread download success!");
                        callbacks.onThreadSaveSuccess();
                    }, (error) -> {
                        Logger.d(TAG, "Thread download error");
                        deleteThread(thread);
                        callbacks.onThreadSaveError(error);
                    });

        } catch (Throwable error) {
            Logger.e(TAG, "Could not save thread", error);
            deleteThread(thread);

            callbacks.onThreadSaveError(error);
        }
    }

    private int getImagesCount(List<Post> posts) {
        int imagesCount = 0;

        for (Post post : posts) {
            imagesCount += post.images.size();
        }

        return imagesCount;
    }

    private void updateProgress(
            ThreadSaveManagerCallbacks callbacks,
            AtomicInteger downloadedFiles,
            int totalImagesCount) {
        int percent = (int)(((float) downloadedFiles.incrementAndGet() / (float) totalImagesCount) * 100f);
        callbacks.onThreadSavingProgress(percent);
    }

    private void downloadSpoilerImage(
            ChanThread thread,
            File threadSaveDirImages,
            Pair<String, HttpUrl> spoilerImageUrlAndExtension) {
        if (thread.loadable.board.spoilers && spoilerImageUrlAndExtension != null) {
            try {
                String spoilerImageName = "spoiler" + "." + spoilerImageUrlAndExtension.first;
                File spoilerImageFullPath = new File(threadSaveDirImages, spoilerImageName);

                if (spoilerImageFullPath.exists()) {
                    return;
                }

                downloadImage(
                        threadSaveDirImages,
                        spoilerImageName,
                        spoilerImageUrlAndExtension.second);
            } catch (IOException e) {
                Exceptions.propagate(e);
            }
        }
    }

    @Nullable
    private Pair<String, HttpUrl> getSpoilerImageUrlAndExtension(List<Post> posts) {
        for (Post post : posts) {
            if (post.images.size() > 0) {
                return new Pair<>(
                        post.images.get(0).extension,
                        post.images.get(0).spoilerThumbnailUrl);
            }
        }

        return null;
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
            AtomicInteger downloadedFiles,
            int totalImagesCount,
            ThreadSaveManagerCallbacks callbacks) {
        return Flowable.fromIterable(post.images)
                .flatMapCompletable((postImage) -> {
                    // Download each image in parallel using executorService
                    return Completable.fromRunnable(() -> {
                        try {
                            downloadImageIntoFile(
                                    threadSaveDirImages,
                                    postImage.filename,
                                    postImage.extension,
                                    postImage.imageUrl,
                                    postImage.thumbnailUrl);
                        } catch (IOException e) {
                            Exceptions.propagate(e);
                        } finally {
                            updateProgress(callbacks, downloadedFiles, totalImagesCount);
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
            String filename,
            String extension,
            HttpUrl imageUrl,
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

    private String getBoardSubDir(ChanThread thread) {
        // saved_threads/4chan/g/

        return "saved_threads" +
                File.separator +
                thread.loadable.site.name() +
                File.separator +
                thread.loadable.boardCode;
    }

    public interface ThreadSaveManagerCallbacks {
        void onThreadSavingStarted();
        void onThreadSavingProgress(int percent);
        void onThreadSaveSuccess();
        void onThreadSaveError(Throwable error);
    }
}
