package com.github.adamantcheese.chan.core.manager;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedThreadManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType;

public class ThreadSaveManager {
    private static final String TAG = "ThreadSaveManager";
    private static final int OKHTTP_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    public static final String SAVED_THREADS_DIR_NAME = "saved_threads";
    public static final String IMAGES_DIR_NAME = "images";
    public static final String SPOILER_FILE_NAME = "spoiler";
    public static final String THUMBNAIL_FILE_NAME = "thumbnail";
    public static final String ORIGINAL_FILE_NAME = "original";

    private Gson gson;
    private DatabaseManager databaseManager;
    private DatabaseSavedThreadManager databaseSavedThreadManager;
    private SavedThreadLoaderRepository savedThreadLoaderRepository;

    private ConcurrentHashMap<Loadable, Boolean> activeDownloads = new ConcurrentHashMap<>();

    /**
     * Prefetch download is active when user clicks the save thread button and a dialog with progressbar
     * shows up. This dialog is supposed to not be cancelable so only one prefetch download should
     * be active at a time. If user rotates a phone or application configuration changes for some
     * different reason we want to remove the callback to avoid memory leaks.
     * */
    private AtomicReference<DownloadingCallback> prefetchDownload = new AtomicReference<>(null);

    private OkHttpClient okHttpClient = new OkHttpClient()
            .newBuilder()
            .writeTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    // TODO: get permissions
    // TODO: don't use postImage extension for thumbnail (they are probably either jpegs or pngs)
    // TODO: check whenther pin watcher is on or off before start saving a thread
    // TODO: show progressbar when preloading a thread
    // TODO: do not forget about clearing composite distosables

    @Inject
    public ThreadSaveManager(
            Gson gson,
            DatabaseManager databaseManager,
            SavedThreadLoaderRepository savedThreadLoaderRepository) {
        this.gson = gson;
        this.databaseManager = databaseManager;
        this.savedThreadLoaderRepository = savedThreadLoaderRepository;
        this.databaseSavedThreadManager = databaseManager.getDatabaseSavedThreadmanager();
    }

    /**
     * Used to remove callback from prefetchDownload atomic reference to avoid memory leaks.
     * */
    public void removePrefetchDownloadCallback() {
        prefetchDownload.set(null);
    }

    /**
     * Saves a thread's posts with all the images/webm/etc to the disk. Callback parameter is used for
     * when user clicks "save thread" button to show downloading progress. Callback may be removed at
     * any time during the download.
     * */
    public Single<Boolean> saveThread(
            Loadable loadable,
            List<Post> postsToSave,
            @Nullable DownloadingCallback callback) {
        if (activeDownloads.containsKey(loadable)) {
            Logger.d(TAG, "Downloader is already running for " + loadable);
            return Single.just(true);
        }

        if (callback != null) {
            if (!prefetchDownload.compareAndSet(null, callback)) {
                throw new RuntimeException("Cannot have more than one prefetch download at a time!");
            }
        }

        return saveThreadInternal(loadable, postsToSave)
                .doOnSubscribe((disposable) -> activeDownloads.put(loadable, true))
                .doFinally(() -> {
                    activeDownloads.remove(loadable);
                    prefetchDownload.compareAndSet(callback, null);
                })
                .subscribeOn(Schedulers.from(executorService));
    }

    // TODO: We should load images before post filtering, because if an image was not downloaded
    //  because of an error it won't be re downloaded next time since the post with the image will
    //  be filtered
    @SuppressLint("CheckResult")
    private Single<Boolean> saveThreadInternal(Loadable loadable, List<Post> postsToSave) {
        return Single.fromCallable(() -> {
            Logger.d(TAG, "Starting a new download for " + loadable + ", on thread " + Thread.currentThread().getName());

            if (BackgroundUtils.isMainThread()) {
                throw new RuntimeException("Cannot be executed on the main thread!");
            }

            if (loadable == null) {
                throw new NullPointerException("loadable is null");
            }

            // Filter out already saved posts and sort new posts in ascending order
            List<Post> newPosts = filterAndSortPosts(loadable.id, postsToSave);
            if (newPosts.isEmpty()) {
                Logger.d(TAG, "No new posts for a thread " + loadable.no);
                return false;
            }

            int postsWithImages = calculateAmountOfPostsWithImages(newPosts);
            Logger.d(TAG, "" + newPosts.size() + " new posts for a thread " + loadable.no + ", with images " + postsWithImages);

            String threadSubDir = getThreadSubDir(loadable, loadable.no);
            File threadSaveDir = new File(ChanSettings.saveLocation.get(), threadSubDir);

            if (!threadSaveDir.exists()) {
                if (!threadSaveDir.mkdirs()) {
                    throw new IOException("Could not create a directory to save the thread " +
                            "to (full path: " + threadSaveDir.getAbsolutePath() + ")");
                }
            }

            File threadSaveDirImages = new File(threadSaveDir, IMAGES_DIR_NAME);
            if (!threadSaveDirImages.exists()) {
                if (!threadSaveDirImages.mkdirs()) {
                    throw new IOException("Could not create a directory to save the thread images" +
                            "to (full path: " + threadSaveDirImages.getAbsolutePath() + ")");
                }
            }

            String boardSubDir = getBoardSubDir(loadable);

            File boardSaveDir = new File(ChanSettings.saveLocation.get(), boardSubDir);
            if (!boardSaveDir.exists()) {
                if (!boardSaveDir.mkdirs()) {
                    throw new IOException("Could not create a directory to save the spoiler image " +
                            "to (full path: " + boardSaveDir.getAbsolutePath() + ")");
                }
            }

            sendStartEvent();

            // Get spoiler image url and extension
            // Pair<Extension, Url>
            final Pair<String, HttpUrl> spoilerImageUrlAndExtension =
                    getSpoilerImageUrlAndExtension(newPosts);

            // Try to load old serialized thread
            @Nullable SerializableThread serializableThread =
                    savedThreadLoaderRepository.loadOldThreadFromJsonFile(threadSaveDir);

            // Add new posts to the already saved posts (if there are any)
            savedThreadLoaderRepository.savePostsToJsonFile(
                    serializableThread,
                    newPosts,
                    threadSaveDir);

            AtomicInteger currentImageDownloadIndex = new AtomicInteger(0);

            try {
                Single.fromCallable(() -> downloadSpoilerImage(
                        loadable,
                        boardSaveDir,
                        spoilerImageUrlAndExtension)
                )
                .flatMap((res) -> {
                    // for each post create a new flowable
                    return Flowable.fromIterable(newPosts)
                            // Here we create a separate reactive stream for each image request.
                            //                   |
                            //                 / | \
                            //                /  |  \
                            //               /   |   \
                            //               V   V   V // Separate streams,
                            //               |   |   |
                            //               o   o   o // Download images in parallel
                            //               |   |   | // (availableProcessors count at a time),
                            //               V   V   V // Combine them back to a single stream,
                            //               \   |   /
                            //                \  |  /
                            //                 \ | /
                            //                   |
                            .flatMap((post) -> {
                                return downloadImages(
                                        threadSaveDirImages,
                                        post,
                                        currentImageDownloadIndex,
                                        postsWithImages);
                            })
                            .toList()
                            .doOnSuccess((list) -> Logger.d(TAG, "result = " + list));
                })
                .flatMap((res) -> {
                    return Single.defer(() -> {
                        updateLastSavedPostNo(loadable, newPosts);

                        Logger.d(TAG, "Successfully updated a thread " + loadable.no);
                        return Single.just(true);
                    });
                })
                // Have to use blockingGet here
                .blockingGet();

                Logger.d(TAG, "All threads are updated");
                return true;
            } catch (Throwable error) {
                // TODO: should we really delete everything upon error?
                deleteThread(loadable, loadable.no);
                throw new Exception(error);
            } finally {
                sendEndEvent();
            }
        });
    }

    private void updateLastSavedPostNo(Loadable loadable, List<Post> newPosts) {
        // Update the latests saved post id in the database
        int lastPostNo = newPosts.get(newPosts.size() - 1).no;
        databaseManager.runTask(databaseSavedThreadManager.updateLastSavedPostNo(loadable.id, lastPostNo));
    }

    private int calculateAmountOfPostsWithImages(List<Post> newPosts) {
        int count = 0;

        for (Post post : newPosts) {
            if (post.images.size() > 0) {
                ++count;
            }
        }

        return count;
    }

    private List<Post> filterAndSortPosts(int loadableId, List<Post> inputPosts) {
        // Filter out already saved posts (by lastSavedPostNo)
        int lastSavedPostNo = databaseManager.runTask(databaseSavedThreadManager.getLastSavedPostNo(loadableId));
        List<Post> filteredPosts = new ArrayList<>(inputPosts.size() / 2);

        for (Post post : inputPosts) {
            if (post.no > lastSavedPostNo) {
                filteredPosts.add(post);
            }
        }

        if (filteredPosts.isEmpty()) {
            return Collections.emptyList();
        }

        // And sort them
        List<Post> posts = new ArrayList<>(filteredPosts);
        Collections.sort(posts, postComparator);

        return posts;
    }

    private boolean downloadSpoilerImage(
            Loadable loadable,
            File threadSaveDirImages,
            Pair<String, HttpUrl> spoilerImageUrlAndExtension) throws IOException {
        // If the board uses spoiler image - download it
        if (loadable.board.spoilers && spoilerImageUrlAndExtension != null) {
            String spoilerImageName = SPOILER_FILE_NAME + "." + spoilerImageUrlAndExtension.first;
            File spoilerImageFullPath = new File(threadSaveDirImages, spoilerImageName);

            if (spoilerImageFullPath.exists()) {
                // Do nothing if already downloaded
                return false;
            }

            downloadImage(
                    threadSaveDirImages,
                    spoilerImageName,
                    spoilerImageUrlAndExtension.second);
        }

        return true;
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

    private Flowable<Boolean> downloadImages(
            File threadSaveDirImages,
            Post post,
            AtomicInteger currentImageDownloadIndex,
            int postsWithImagesCount) {
        if (post.images.isEmpty()) {
            Logger.d(TAG, "Post " + post.no + " contains no images");
            return Flowable.empty();
        }

        if (!shouldDownloadImages()) {
            Logger.d(TAG, "Cannot load images or videos with current network");
            return Flowable.empty();
        }

        return Flowable.fromIterable(post.images)
                .flatMapSingle((postImage) -> {
                    // Download each image in parallel using executorService
                    return Single.defer(() -> {
                        try {
                            downloadImageIntoFile(
                                    threadSaveDirImages,
                                    postImage.originalName,
                                    postImage.extension,
                                    postImage.imageUrl,
                                    postImage.thumbnailUrl);
                        } catch (IOException error) {
                            Logger.e(TAG, "downloadImageIntoFile error for image "
                                    + postImage.filename + " %s", error.getMessage());

                            deleteImageCompletely(
                                    threadSaveDirImages,
                                    postImage.originalName,
                                    postImage.extension);
                            throw error;
                        }

                        return Single.just(true);
                    })
                    // We don't really want to use more than "CPU processors count" threads here, but
                    // we want for every request to run on a separate thread in parallel
                    .subscribeOn(Schedulers.from(executorService))
                    // Retry couple of times upon exceptions
                    .retry(MAX_RETRY_ATTEMPTS)
                    // Do nothing if an error occurs (like timeout exception) because we don't want
                    // to lose what we have already downloaded
                    .doOnError((error) -> {
                        Logger.e(TAG, "Error while trying to download image " + postImage.originalName, error);
                    })
                    .doOnEvent((result, event) -> {
                        sendProgressEvent(currentImageDownloadIndex, postsWithImagesCount);
                    })
                    .onErrorReturnItem(false);
                });
    }

    private void deleteImageCompletely(
            File threadSaveDirImages,
            String filename,
            String extension) {
        Logger.d(TAG, "Deleting a file with name " + filename);
        boolean error = false;

        File originalFile = new File(threadSaveDirImages,
                filename + "_" + ORIGINAL_FILE_NAME + "." + extension);
        if (originalFile.exists()) {
            if (!originalFile.delete()) {
                error = true;
            }
        }

        File thumbnailFile = new File(threadSaveDirImages,
                filename + "_" + THUMBNAIL_FILE_NAME + "." + extension);
        if (thumbnailFile.exists()) {
            if (!thumbnailFile.delete()) {
                error = true;
            }
        }

        if (error) {
            Logger.e(TAG, "Could not completely delete image " + filename);
        }
    }

    private void downloadImageIntoFile(
            File threadSaveDirImages,
            String filename,
            String extension,
            HttpUrl imageUrl,
            HttpUrl thumbnailUrl) throws IOException {
        Logger.d(TAG, "Downloading a file with name " + filename + " on a thread " + Thread.currentThread().getName());

        downloadImage(
                threadSaveDirImages,
                filename + "_" + ORIGINAL_FILE_NAME + "." + extension,
                imageUrl);
        downloadImage(
                threadSaveDirImages,
                filename + "_" + THUMBNAIL_FILE_NAME + "." + extension,
                thumbnailUrl);
    }

    private boolean shouldDownloadImages() {
        return shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get()) &&
                shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
    }

    private void downloadImage(
            File threadSaveDirImages,
            String filename,
            HttpUrl imageUrl) throws IOException {
        File imageFile = new File(threadSaveDirImages, filename);
        if (!imageFile.exists()) {
            Request request = new Request.Builder().url(imageUrl).build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.code() != 200) {
                    Logger.d(TAG, "Download image request returned bad status code: " + response.code());
                    return;
                }

                storeImageToFile(imageFile, response);
                Logger.d(TAG, "Downloaded a file with name " + filename);
            }
        } else {
            Logger.d(TAG, "image " + filename + " already exists on the disk, skip it");
        }
    }

    private void storeImageToFile(
            File imageFile,
            Response response) throws IOException {
        if (!imageFile.createNewFile()) {
            throw new IOException("Could not create a file to save an image to (path: "
                    + imageFile.getAbsolutePath() + ")");
        }

        try (ResponseBody body = response.body()) {
            if (body == null) {
                throw new NullPointerException("Response body is null");
            }

            if (body.contentLength() <= 0) {
                throw new IOException("Image body is empty");
            }

            try (InputStream is = body.byteStream()) {
                try (OutputStream os = new FileOutputStream(imageFile)) {
                    IOUtils.copy(is, os);
                }
            }
        }
    }

    private void deleteThread(Loadable loadable, int opNo) throws IOException {
        String subDirs = getThreadSubDir(loadable, opNo);

        File threadSaveDir = new File(ChanSettings.saveLocation.get(), subDirs);
        if (!threadSaveDir.exists()) {
            return;
        }

        IOUtils.deleteDirWithContents(threadSaveDir.getParentFile());
    }

    private void sendStartEvent() {
        DownloadingCallback callback = prefetchDownload.get();
        if (callback != null) {
            Logger.d(TAG, "Started downloading thread");
            callback.onStartedDownloading();
        }
    }

    private void sendProgressEvent(AtomicInteger currentImageDownloadIndex, int postsWithImagesCount) {
        DownloadingCallback callback = prefetchDownload.get();
        if (callback != null) {
            // postsWithImagesCount may be 0 so we need to avoid division by zero
            int count = postsWithImagesCount == 0 ? 1 : postsWithImagesCount;
            int index = currentImageDownloadIndex.incrementAndGet();
            int percent = (int)(((float)index / (float) count) * 100f);

            Logger.d(TAG, "Downloading is in progress, " +
                    "percent = " + percent +
                    ", count = " + count +
                    ", index = " + index);
            callback.onProgress(percent);
        }
    }

    private void sendEndEvent() {
        DownloadingCallback callback = prefetchDownload.get();
        if (callback != null) {
            Logger.d(TAG, "Completed downloading thread");
            callback.onCompletedDownloading();
        }
    }

    public static String formatThumbnailImageName(String originalName, String extension) {
        return originalName + "_" + THUMBNAIL_FILE_NAME + "." + extension;
    }

    public static String formatOriginalImageName(String originalName, String extension) {
        return originalName + "_" + ORIGINAL_FILE_NAME + "." + extension;
    }

    public static String getThreadSubDir(Loadable loadable, int opNo) {
        // saved_threads/4chan/g/11223344

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode +
                File.separator + opNo;
    }

    public static String getImagesSubDir(Loadable loadable, int opNo) {
        // saved_threads/4chan/g/11223344

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode +
                File.separator + opNo +
                File.separator + IMAGES_DIR_NAME;
    }

    public static String getBoardSubDir(Loadable loadable) {
        // saved_threads/4chan/g/

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode;
    }

    public interface DownloadingCallback {
        void onStartedDownloading();
        void onProgress(int percent);
        void onCompletedDownloading();
    }

    private static final Comparator<Post> postComparator = (o1, o2) -> Integer.compare(o1.no, o2.no);
}
