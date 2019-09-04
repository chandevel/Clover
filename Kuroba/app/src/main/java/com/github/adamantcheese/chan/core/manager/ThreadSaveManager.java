package com.github.adamantcheese.chan.core.manager;

import android.annotation.SuppressLint;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedThreadManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ThreadSaveManager {
    private static final String TAG = "ThreadSaveManager";
    private static final int OKHTTP_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final boolean VERBOSE_LOG = false;

    public static final String SAVED_THREADS_DIR_NAME = "saved_threads";
    public static final String IMAGES_DIR_NAME = "images";
    public static final String SPOILER_FILE_NAME = "spoiler";
    public static final String THUMBNAIL_FILE_NAME = "thumbnail";
    public static final String ORIGINAL_FILE_NAME = "original";
    public static final String NO_MEDIA_FILE_NAME = ".nomedia";

    private DatabaseManager databaseManager;
    private DatabaseSavedThreadManager databaseSavedThreadManager;
    private SavedThreadLoaderRepository savedThreadLoaderRepository;

    @GuardedBy("itself")
    private final Map<Loadable, SaveThreadParameters> activeDownloads = new HashMap<>();
    @GuardedBy("activeDownloads")
    private final Map<Loadable, AdditionalThreadParameters> additionalThreadParameter = new HashMap<>();

    private OkHttpClient okHttpClient = new OkHttpClient()
            .newBuilder()
            .writeTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService
            = Executors.newFixedThreadPool(getThreadsCountForDownloaderExecutor());

    private PublishProcessor<Loadable> workerQueue = PublishProcessor.create();

    private static int getThreadsCountForDownloaderExecutor() {
        int threadsCount = (Runtime.getRuntime().availableProcessors() / 2) + 1;
        if (threadsCount < 3) {
            // We need at least two worker threads and one thread for the rx stream itself. More threads
            // will make the phone laggy, less threads will make downloading really slow.
            threadsCount = 3;
        }

        Logger.d(TAG, "Downloader threads count = " + threadsCount);
        return threadsCount;
    }

    @Inject
    public ThreadSaveManager(
            DatabaseManager databaseManager,
            SavedThreadLoaderRepository savedThreadLoaderRepository) {
        this.databaseManager = databaseManager;
        this.savedThreadLoaderRepository = savedThreadLoaderRepository;
        this.databaseSavedThreadManager = databaseManager.getDatabaseSavedThreadManager();

        initRxWorkerQueue();
    }

    /**
     * Initializes main rx queue that is going to accept new downloading requests and process them
     * sequentially.
     * <p>
     * This class is a singleton so we don't really care about disposing of the rx stream
     */
    @SuppressLint("CheckResult")
    private void initRxWorkerQueue() {
        // Just buffer everything in the internal queue when the consumers are slow (and they are
        // always slow because they have to download images, but we check whether a download request
        // is already enqueued so it's okay for us to rely on the buffering)
        workerQueue.onBackpressureBuffer().concatMapSingle((loadable) -> {
            SaveThreadParameters parameters;
            List<Post> postsToSave = new ArrayList<>();

            synchronized (activeDownloads) {
                Logger.d(TAG, "New downloading request started " + loadableToString(loadable)
                        + ", activeDownloads count = " + activeDownloads.size());
                parameters = activeDownloads.get(loadable);

                if (parameters != null) {
                    // Use a copy of the list to avoid ConcurrentModificationExceptions
                    postsToSave.addAll(parameters.postsToSave);
                }
            }

            if (parameters == null) {
                Logger.e(TAG, "Could not find download parameters for loadable "
                        + loadableToString(loadable));
                return Single.just(false);
            }

            return saveThreadInternal(loadable, postsToSave)
                    // Use the executor's thread to process the queue elements. Everything above
                    // will executed on this executor's threads.
                    .subscribeOn(Schedulers.from(executorService))
                    // Everything below will be executed on the main thread
                    .observeOn(AndroidSchedulers.mainThread())
                    // Handle errors
                    .doOnError((error) -> onDownloadingError(error, loadable))
                    // Handle results
                    .doOnSuccess((result) -> onDownloadingCompleted(result, loadable))
                    .doOnEvent((result, error) -> {
                        synchronized (activeDownloads) {
                            Logger.d(TAG, "Downloading request has completed for loadable "
                                    + loadableToString(loadable) +
                                    ", activeDownloads count = "
                                    + activeDownloads.size());
                        }
                    })
                    // Suppress all of the exceptions so that the stream does not complete
                    .onErrorReturnItem(false);
        }).subscribe(
                (res) -> {
                },
                (error) -> Logger.e(TAG, "Uncaught exception!!! workerQueue is in error state now!!! This should not happen!!!", error),
                () -> Logger.e(TAG, "workerQueue stream has completed!!! This should not happen!!!"));
    }

    /**
     * Enqueues a thread's posts with all the images/webm/etc to be saved to the disk.
     */
    public void enqueueThreadToSave(
            Loadable loadable,
            List<Post> postsToSave) {
        if (!BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Must be executed on the main thread");
        }

        synchronized (activeDownloads) {
            // Check if a thread is already being downloaded
            if (activeDownloads.containsKey(loadable)) {
                if (VERBOSE_LOG) {
                    Logger.d(TAG, "Downloader is already running for " + loadableToString(loadable));
                }
                return;
            }
        }

        SaveThreadParameters parameters = new SaveThreadParameters(
                loadable,
                postsToSave);

        // Store the parameters of this download
        synchronized (activeDownloads) {
            activeDownloads.put(loadable, parameters);

            if (!additionalThreadParameter.containsKey(loadable)) {
                additionalThreadParameter.put(loadable, new AdditionalThreadParameters());
            }
        }

        // Enqueue the download
        workerQueue.onNext(loadable);
    }

    /**
     * Cancels all downloads
     */
    public void cancelAllDownloading() {
        synchronized (activeDownloads) {
            for (Map.Entry<Loadable, SaveThreadParameters> entry : activeDownloads.entrySet()) {
                SaveThreadParameters parameters = entry.getValue();

                parameters.cancel();
            }

            additionalThreadParameter.clear();
        }
    }

    /**
     * Cancels a download associated with this loadable. Cancelling means that the user has completely
     * removed the pin associated with it. This means that we need to delete thread's files from the disk
     * as well.
     */
    public void cancelDownloading(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters == null) {
                Logger.w(TAG, "cancelDownloading Could not find SaveThreadParameters for loadable "
                        + loadableToString(loadable));
                return;
            }

            Logger.d(TAG, "Cancelling a download for loadable " + loadableToString(loadable));
            additionalThreadParameter.remove(loadable);
            parameters.cancel();
        }
    }

    /**
     * Stops a download associated with this loadable. Stopping means that the user unpressed
     * "save thread" button. This does not mean that they do not want to save this thread anymore
     * they may press it again later. So we don't need to delete thread's files from the disk in this
     * case.
     */
    public void stopDownloading(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters == null) {
                Logger.w(TAG, "stopDownloading Could not find SaveThreadParameters for loadable "
                        + loadableToString(loadable));
                return;
            }

            Logger.d(TAG, "Stopping a download for loadable " + loadableToString(loadable));
            parameters.stop();
        }
    }

    private void onDownloadingCompleted(boolean result, Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters saveThreadParameters = activeDownloads.get(loadable);
            if (saveThreadParameters == null) {
                Logger.w(TAG, "Attempt to remove non existing active download with loadable "
                        + loadableToString(loadable));
                return;
            }

            Logger.d(TAG, "Download for loadable " + loadableToString(loadable)
                    + " ended up with result " + result);

            // Remove the download
            activeDownloads.remove(loadable);
        }
    }

    private void onDownloadingError(Throwable error, Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters saveThreadParameters = activeDownloads.get(loadable);
            if (saveThreadParameters == null) {
                Logger.w(TAG, "Attempt to remove non existing active download with loadable "
                        + loadableToString(loadable));
                return;
            }

            if (isFatalException(error)) {
                Logger.d(TAG, "Download for loadable " + loadableToString(loadable)
                        + " ended up with an error", error);
            }

            // Remove the download
            activeDownloads.remove(loadable);
        }
    }

    /**
     * Saves new posts to the disk asynchronously. Does duplicates checking internally so it is
     * safe to just pass all posts in there. Checks whether the current loadable is already
     * being downloaded. Checks for images that couldn't be downloaded on the previous attempt
     * (because of IO errors/bad network/server being down etc)
     *
     * @param loadable    is a unique identifier of a thread we are saving.
     * @param postsToSave posts of a thread to be saved.
     */
    @SuppressLint("CheckResult")
    private Single<Boolean> saveThreadInternal(@NonNull Loadable loadable, List<Post> postsToSave) {
        return Single.fromCallable(() -> {
            if (BackgroundUtils.isMainThread()) {
                throw new RuntimeException("Cannot be executed on the main thread!");
            }

            if (!isCurrentDownloadRunning(loadable)) {
                // This download was cancelled or stopped while waiting in the queue.
                Logger.d(TAG, "Download for loadable " + loadableToString(loadable) +
                        " was canceled or stopped while it was waiting in the queue");
                return false;
            }

            Logger.d(TAG, "Starting a new download for " + loadableToString(loadable) +
                    ", on thread " + Thread.currentThread().getName());

            String threadSubDir = getThreadSubDir(loadable);
            File threadSaveDir = new File(ChanSettings.saveLocation.get(), threadSubDir);

            if (!threadSaveDir.exists() && !threadSaveDir.mkdirs()) {
                throw new CouldNotCreateThreadDirectoryException(threadSaveDir);
            }

            File threadSaveDirImages = new File(threadSaveDir, IMAGES_DIR_NAME);
            if (!threadSaveDirImages.exists() && !threadSaveDirImages.mkdirs()) {
                throw new CouldNotCreateImagesDirectoryException(threadSaveDirImages);
            }

            if (!ChanSettings.allowMediaScannerToScanLocalThreads.get()) {
                // .nomedia file being in the images directory "should" prevent media scanner from
                // scanning this directory
                File noMediaFile = new File(threadSaveDirImages, NO_MEDIA_FILE_NAME);
                if (!noMediaFile.exists() && !noMediaFile.createNewFile()) {
                    throw new CouldNotCreateNoMediaFile(threadSaveDirImages);
                }
            } else {
                File noMediaFile = new File(threadSaveDirImages, NO_MEDIA_FILE_NAME);
                if (noMediaFile.exists() && !noMediaFile.delete()) {
                    Logger.e(TAG, "Could not delete .nomedia file from directory "
                            + threadSaveDirImages.getAbsolutePath());
                }
            }

            // Filter out already saved posts and sort new posts in ascending order
            List<Post> newPosts = filterAndSortPosts(threadSaveDirImages, loadable, postsToSave);
            if (newPosts.isEmpty()) {
                Logger.d(TAG, "No new posts for a thread " + loadableToString(loadable));
                throw new NoNewPostsToSaveException();
            }

            int postsWithImages = calculateAmountOfPostsWithImages(newPosts);
            int maxImageIoErrors = calculateMaxImageIoErrors(postsWithImages);

            Logger.d(TAG, "" + newPosts.size() + " new posts for a thread " +
                    loadable.no + ", with images " + postsWithImages);

            String boardSubDir = getBoardSubDir(loadable);

            File boardSaveDir = new File(ChanSettings.saveLocation.get(), boardSubDir);
            if (!boardSaveDir.exists() && !boardSaveDir.mkdirs()) {
                throw new CouldNotCreateSpoilerImageDirectoryException(boardSaveDir);
            }

            // Get spoiler image url
            final HttpUrl spoilerImageUrl = getSpoilerImageUrl(newPosts);

            // Try to load old serialized thread
            @Nullable SerializableThread serializableThread =
                    savedThreadLoaderRepository.loadOldThreadFromJsonFile(threadSaveDir);

            // Add new posts to the already saved posts (if there are any)
            savedThreadLoaderRepository.savePostsToJsonFile(
                    serializableThread,
                    newPosts,
                    threadSaveDir);

            AtomicInteger currentImageDownloadIndex = new AtomicInteger(0);
            AtomicInteger imageDownloadsWithIoError = new AtomicInteger(0);

            try {
                Single.fromCallable(() -> downloadSpoilerImage(
                        loadable,
                        boardSaveDir,
                        spoilerImageUrl)
                )
                        .flatMap((res) -> {
                            // For each post create a new inner rx stream (so they can be processed in parallel)
                            return Flowable.fromIterable(newPosts)
                                    // Here we create a separate reactive stream for each image request.
                                    // But we use an executor service with limited threads amount, so there
                                    // will be only this much at a time.
                                    //                   |
                                    //                 / | \
                                    //                /  |  \
                                    //               /   |   \
                                    //               V   V   V // Separate streams.
                                    //               |   |   |
                                    //               o   o   o // Download images in parallel.
                                    //               |   |   |
                                    //               V   V   V // Combine them back to a single stream.
                                    //               \   |   /
                                    //                \  |  /
                                    //                 \ | /
                                    //                   |
                                    .flatMap((post) -> downloadImages(
                                            loadable,
                                            threadSaveDirImages,
                                            post,
                                            currentImageDownloadIndex,
                                            postsWithImages,
                                            imageDownloadsWithIoError,
                                            maxImageIoErrors))
                                    .toList()
                                    .doOnSuccess((list) -> Logger.d(TAG, "PostImage download result list = " + list));
                        })
                        .flatMap((res) -> Single.defer(() -> {
                            if (!isCurrentDownloadRunning(loadable)) {
                                if (isCurrentDownloadStopped(loadable)) {
                                    Logger.d(TAG, "Thread downloading has been stopped "
                                            + loadableToString(loadable));
                                } else {
                                    Logger.d(TAG, "Thread downloading has been canceled "
                                            + loadableToString(loadable));
                                }

                                return Single.just(false);
                            }

                            updateLastSavedPostNo(loadable, newPosts);

                            Logger.d(TAG, "Successfully updated a thread " + loadableToString(loadable));
                            return Single.just(true);
                        }))
                        // Have to use blockingGet here. This is a place where all of the exception will come
                        // out from
                        .blockingGet();
            } finally {
                if (shouldDeleteDownloadedFiles(loadable)) {
                    if (isCurrentDownloadStopped(loadable)) {
                        Logger.d(TAG, "Thread with loadable " + loadableToString(loadable)
                                + " has been stopped");
                    } else {
                        Logger.d(TAG, "Thread with loadable " + loadableToString(loadable)
                                + " has been canceled");
                    }

                    deleteThreadFilesFromDisk(loadable);
                } else {
                    Logger.d(TAG, "Thread with loadable " + loadableToString(loadable)
                            + " has been updated");
                }
            }

            return true;
        });
    }

    private int calculateMaxImageIoErrors(int postsWithImages) {
        int maxIoErrors = (int) (((float) postsWithImages / 100f) * 5f);
        if (maxIoErrors == 0) {
            maxIoErrors = 1;
        }

        return maxIoErrors;
    }

    /**
     * To avoid saving the same posts every time we need to update LastSavedPostNo in the DB
     */
    private void updateLastSavedPostNo(Loadable loadable, List<Post> newPosts) {
        // Update the latests saved post id in the database
        int lastPostNo = newPosts.get(newPosts.size() - 1).no;
        databaseManager.runTask(databaseSavedThreadManager.updateLastSavedPostNo(loadable.id, lastPostNo));
    }

    /**
     * Calculates how many posts with images we have in total
     */
    private int calculateAmountOfPostsWithImages(List<Post> newPosts) {
        int count = 0;

        for (Post post : newPosts) {
            if (post.images.size() > 0) {
                ++count;
            }
        }

        return count;
    }

    /**
     * Returns only the posts that we haven't saved yet. Sorts them in ascending order.
     * If a post has at least one image that has not been downloaded yet it will be
     * redownloaded again
     */
    private List<Post> filterAndSortPosts(File threadSaveDirImages, Loadable loadable, List<Post> inputPosts) {
        // Filter out already saved posts (by lastSavedPostNo)
        int lastSavedPostNo = databaseManager.runTask(databaseSavedThreadManager.getLastSavedPostNo(loadable.id));

        // Use HashSet to avoid duplicates
        Set<Post> filteredPosts = new HashSet<>(inputPosts.size() / 2);

        // lastSavedPostNo == 0 means that we don't have this thread downloaded yet
        if (lastSavedPostNo > 0) {
            for (Post post : inputPosts) {
                if (!checkWhetherAllPostImagesAreAlreadySaved(threadSaveDirImages, post)) {
                    // Some of the post's images could not be downloaded during the previous download
                    // so we need to download them now
                    if (VERBOSE_LOG) {
                        Logger.d(TAG, "Found not downloaded yet images for a post " + post.no +
                                ", for loadable " + loadableToString(loadable));
                    }

                    filteredPosts.add(post);
                    continue;
                }

                if (post.no > lastSavedPostNo) {
                    filteredPosts.add(post);
                }
            }
        } else {
            filteredPosts.addAll(inputPosts);
        }

        if (filteredPosts.isEmpty()) {
            return Collections.emptyList();
        }

        // And sort them
        List<Post> posts = new ArrayList<>(filteredPosts);
        Collections.sort(posts, postComparator);

        return posts;
    }

    private boolean checkWhetherAllPostImagesAreAlreadySaved(File threadSaveDirImages, Post post) {
        for (PostImage postImage : post.images) {
            {
                String originalImageFilename = postImage.originalName + "_"
                        + ORIGINAL_FILE_NAME + "." + postImage.extension;

                File originalImage = new File(threadSaveDirImages, originalImageFilename);
                if (!originalImage.exists()) {
                    return false;
                }

                if (!originalImage.canRead()) {
                    if (!originalImage.delete()) {
                        Logger.e(TAG, "Could not delete originalImage with path "
                                + originalImage.getAbsolutePath());
                    }
                    return false;
                }

                if (originalImage.length() == 0L) {
                    if (!originalImage.delete()) {
                        Logger.e(TAG, "Could not delete originalImage with path "
                                + originalImage.getAbsolutePath());
                    }
                    return false;
                }
            }

            {
                String thumbnailExtension = StringUtils.extractFileExtensionFromImageUrl(
                        postImage.thumbnailUrl.toString());
                String thumbnailImageFilename = postImage.originalName + "_"
                        + THUMBNAIL_FILE_NAME + "." + thumbnailExtension;

                File thumbnailImage = new File(threadSaveDirImages, thumbnailImageFilename);
                if (!thumbnailImage.exists()) {
                    return false;
                }

                if (!thumbnailImage.canRead()) {
                    if (!thumbnailImage.delete()) {
                        Logger.e(TAG, "Could not delete thumbnailImage with path "
                                + thumbnailImage.getAbsolutePath());
                    }
                    return false;
                }

                if (thumbnailImage.length() == 0L) {
                    if (!thumbnailImage.delete()) {
                        Logger.e(TAG, "Could not delete thumbnailImage with path "
                                + thumbnailImage.getAbsolutePath());
                    }
                    return false;
                }
            }
        }

        return true;
    }

    private boolean downloadSpoilerImage(
            Loadable loadable,
            File threadSaveDirImages,
            HttpUrl spoilerImageUrl) throws IOException {
        // If the board uses spoiler image - download it
        if (loadable.board.spoilers && spoilerImageUrl != null) {
            String spoilerImageExtension = StringUtils.extractFileExtensionFromImageUrl(
                    spoilerImageUrl.toString());
            if (spoilerImageExtension == null) {
                Logger.e(TAG, "Could not extract spoiler image extension from url, spoilerImageUrl = "
                        + spoilerImageUrl.toString());
                return false;
            }

            String spoilerImageName = SPOILER_FILE_NAME + "." + spoilerImageExtension;
            File spoilerImageFullPath = new File(threadSaveDirImages, spoilerImageName);

            if (spoilerImageFullPath.exists()) {
                // Do nothing if already downloaded
                return false;
            }

            try {
                downloadImage(
                        loadable,
                        threadSaveDirImages,
                        spoilerImageName,
                        spoilerImageUrl);
            } catch (ImageWasAlreadyDeletedException e) {
                // If this ever happens that means that something has changed on the server
                Logger.e(TAG, "Could not download spoiler image, got 404 for loadable "
                        + loadableToString(loadable));
                return false;
            }
        }

        return true;
    }

    @Nullable
    private HttpUrl getSpoilerImageUrl(List<Post> posts) {
        for (Post post : posts) {
            if (post.images.size() > 0) {
                return post.images.get(0).spoilerThumbnailUrl;
            }
        }

        return null;
    }

    private Flowable<Boolean> downloadImages(
            Loadable loadable,
            File threadSaveDirImages,
            Post post,
            AtomicInteger currentImageDownloadIndex,
            int postsWithImagesCount,
            AtomicInteger imageDownloadsWithIoError,
            int maxImageIoErrors) {
        if (post.images.isEmpty()) {
            if (VERBOSE_LOG) {
                Logger.d(TAG, "Post " + post.no + " contains no images");
            }
            // No images, so return true
            return Flowable.just(true);
        }

        if (!shouldDownloadImages()) {
            if (VERBOSE_LOG) {
                Logger.d(TAG, "Cannot load images or videos with the current network");
            }
            return Flowable.just(false);
        }

        return Flowable.fromIterable(post.images)
                .flatMapSingle((postImage) -> {
                    // Download each image in parallel using executorService
                    return Single.defer(() -> {
                        if (imageDownloadsWithIoError.get() >= maxImageIoErrors) {
                            Logger.d(TAG, "downloadImages terminated due to amount of IOExceptions");
                            return Single.just(false);
                        }

                        String thumbnailExtension = StringUtils.extractFileExtensionFromImageUrl(
                                postImage.thumbnailUrl.toString());

                        if (thumbnailExtension == null) {
                            Logger.d(TAG, "Could not extract thumbnail image extension, thumbnailUrl = "
                                    + postImage.thumbnailUrl.toString());
                            return Single.just(false);
                        }

                        if (postImage.imageUrl == null) {
                            Logger.d(TAG, "postImage.imageUrl == null");
                            return Single.just(false);
                        }

                        try {
                            downloadImageIntoFile(
                                    threadSaveDirImages,
                                    postImage.originalName,
                                    postImage.extension,
                                    thumbnailExtension,
                                    postImage.imageUrl,
                                    postImage.thumbnailUrl,
                                    loadable);
                        } catch (IOException error) {
                            Logger.e(TAG, "downloadImageIntoFile error for image "
                                    + postImage.originalName + ", error message = %s", error.getMessage());

                            deleteImageCompletely(
                                    threadSaveDirImages,
                                    postImage.originalName,
                                    postImage.extension);
                            throw error;
                        } catch (ImageWasAlreadyDeletedException error) {
                            Logger.e(TAG, "Could not download an image " + postImage.originalName
                                    + " for loadable " + loadableToString(loadable) +
                                    ", got 404, adding it to the deletedImages set");

                            addImageToAlreadyDeletedImage(loadable, postImage.originalName);

                            deleteImageCompletely(
                                    threadSaveDirImages,
                                    postImage.originalName,
                                    postImage.extension);
                            return Single.just(false);
                        }

                        return Single.just(true);
                    })
                            // We don't really want to use a lot of threads here so we use an executor with
                            // specified amount of threads
                            .subscribeOn(Schedulers.from(executorService))
                            // Retry couple of times upon exceptions
                            .retry(MAX_RETRY_ATTEMPTS)
                            .doOnError((error) -> {
                                Logger.e(TAG, "Error while trying to download image " + postImage.originalName, error);

                                if (error instanceof IOException) {
                                    imageDownloadsWithIoError.incrementAndGet();
                                }
                            })
                            .doOnEvent((result, event) -> logThreadDownloadingProgress(
                                    loadable,
                                    currentImageDownloadIndex,
                                    postsWithImagesCount))
                            // Do nothing if an error occurs (like timeout exception) because we don't want
                            // to lose what we have already downloaded
                            .onErrorReturnItem(false);
                });
    }

    private boolean isImageAlreadyDeletedFromTheServer(Loadable loadable, String filename) {
        synchronized (activeDownloads) {
            AdditionalThreadParameters parameters = additionalThreadParameter.get(loadable);
            if (parameters == null) {
                Logger.e(TAG, "isImageAlreadyDeletedFromTheServer parameters == null " +
                        "for loadable " + loadableToString(loadable));
                return true;
            }

            return parameters.isImageDeletedFromTheServer(filename);
        }
    }

    private void addImageToAlreadyDeletedImage(Loadable loadable, String originalName) {
        synchronized (activeDownloads) {
            AdditionalThreadParameters parameters = additionalThreadParameter.get(loadable);
            if (parameters == null) {
                Logger.e(TAG, "addImageToAlreadyDeletedImage parameters == null " +
                        "for loadable " + loadableToString(loadable));
                return;
            }

            parameters.addDeletedImage(originalName);
        }
    }

    private void logThreadDownloadingProgress(
            Loadable loadable,
            AtomicInteger currentImageDownloadIndex,
            int postsWithImagesCount) {
        // postsWithImagesCount may be 0 so we need to avoid division by zero
        int count = postsWithImagesCount == 0 ? 1 : postsWithImagesCount;
        int index = currentImageDownloadIndex.incrementAndGet();
        int percent = (int) (((float) index / (float) count) * 100f);

        Logger.d(TAG, "Downloading is in progress for an image with loadable "
                + loadableToString(loadable) +
                ", percent = " + percent +
                ", total = " + count +
                ", current = " + index);
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

    /**
     * Downloads an image with it's thumbnail and stores them to the disk
     */
    private void downloadImageIntoFile(
            File threadSaveDirImages,
            String filename,
            String originalExtension,
            String thumbnailExtension,
            HttpUrl imageUrl,
            HttpUrl thumbnailUrl,
            Loadable loadable) throws IOException, ImageWasAlreadyDeletedException {
        if (isImageAlreadyDeletedFromTheServer(loadable, filename)) {
            // We have already tried to download this image and got 404, so it was probably deleted
            // from the server so there is no point in trying to download it again
            Logger.d(TAG, "Image " + filename + " was already deleted from the server for loadable "
                    + loadableToString(loadable));
            return;
        }

        if (VERBOSE_LOG) {
            Logger.d(TAG, "Downloading a file with name " + filename + " on a thread " +
                    Thread.currentThread().getName() + " for loadable " + loadableToString(loadable));
        }

        downloadImage(
                loadable,
                threadSaveDirImages,
                filename + "_" + ORIGINAL_FILE_NAME + "." + originalExtension,
                imageUrl);
        downloadImage(
                loadable,
                threadSaveDirImages,
                filename + "_" + THUMBNAIL_FILE_NAME + "." + thumbnailExtension,
                thumbnailUrl);
    }

    /**
     * Checks whether the user allows downloading images and other files when there is no Wi-Fi connection
     */
    private boolean shouldDownloadImages() {
        return ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType(ChanSettings.imageAutoLoadNetwork.get()) &&
                ChanSettings.MediaAutoLoadMode.shouldLoadForNetworkType(ChanSettings.videoAutoLoadNetwork.get());
    }

    /**
     * Downloads an image and stores it to the disk
     */
    private void downloadImage(
            Loadable loadable,
            File threadSaveDirImages,
            String filename,
            HttpUrl imageUrl) throws IOException, ImageWasAlreadyDeletedException {
        if (!shouldDownloadImages()) {
            if (VERBOSE_LOG) {
                Logger.d(TAG, "Cannot load images or videos with the current network");
            }
            return;
        }

        if (!isCurrentDownloadRunning(loadable)) {
            if (isCurrentDownloadStopped(loadable)) {
                Logger.d(TAG, "File downloading with name " + filename + " has been stopped");
            } else {
                Logger.d(TAG, "File downloading with name " + filename + " has been canceled");
            }
            return;
        }

        File imageFile = new File(threadSaveDirImages, filename);
        if (!imageFile.exists()) {
            Request request = new Request.Builder().url(imageUrl).build();

            try (Response response = okHttpClient.newCall(request).execute()) {
                if (response.code() != 200) {
                    if (response.code() == 404) {
                        throw new ImageWasAlreadyDeletedException(filename);
                    }

                    throw new IOException("Download image request returned bad status code: " + response.code());
                }

                storeImageToFile(imageFile, response);

                if (VERBOSE_LOG) {
                    Logger.d(TAG, "Downloaded a file with name " + filename);
                }
            }
        } else {
            Logger.d(TAG, "image " + filename + " already exists on the disk, skip it");
        }
    }

    /**
     * @return true when user removes the pin associated with this loadable
     */
    private boolean shouldDeleteDownloadedFiles(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters != null) {
                if (parameters.isCancelled()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isCurrentDownloadStopped(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters != null) {
                if (parameters.isStopped()) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isCurrentDownloadRunning(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters != null) {
                if (parameters.isRunning()) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Writes image's bytes to a file
     */
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

    /**
     * Determines whether we should log this exception or not.
     * For instance NoNewPostsToSaveException will be thrown every time when there are no new
     * posts to download left after filtering.
     */
    private boolean isFatalException(Throwable error) {
        return !(error instanceof NoNewPostsToSaveException);
    }

    /**
     * When user cancels a download we need to delete the thread from the disk as well
     */
    private void deleteThreadFilesFromDisk(Loadable loadable) {
        String subDirs = getThreadSubDir(loadable);

        File threadSaveDir = new File(ChanSettings.saveLocation.get(), subDirs);
        if (!threadSaveDir.exists()) {
            return;
        }

        IOUtils.deleteDirWithContents(threadSaveDir);
    }

    /**
     * Extracts and converts to a string only the info that we are interested in from this loadable
     */
    private String loadableToString(Loadable loadable) {
        return "[" + loadable.site.name() + ", " + loadable.boardCode + ", " + loadable.no + "]";
    }

    public static String formatThumbnailImageName(String originalName, String extension) {
        return originalName + "_" + THUMBNAIL_FILE_NAME + "." + extension;
    }

    public static String formatOriginalImageName(String originalName, String extension) {
        return originalName + "_" + ORIGINAL_FILE_NAME + "." + extension;
    }

    public static String formatSpoilerImageName(String extension) {
        // spoiler.jpg
        return SPOILER_FILE_NAME + "." + extension;
    }

    public static String getThreadSubDir(Loadable loadable) {
        // saved_threads/4chan/g/11223344

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode +
                File.separator + loadable.no;
    }

    public static String getImagesSubDir(Loadable loadable) {
        // saved_threads/4chan/g/11223344/images

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode +
                File.separator + loadable.no +
                File.separator + IMAGES_DIR_NAME;
    }

    public static String getBoardSubDir(Loadable loadable) {
        // saved_threads/4chan/g

        return SAVED_THREADS_DIR_NAME +
                File.separator +
                loadable.site.name() +
                File.separator +
                loadable.boardCode;
    }

    /**
     * The main difference between AdditionalThreadParameters and SaveThreadParameters is that
     * SaveThreadParameters is getting deleted after each thread download attempt while
     * AdditionalThreadParameters stay until app restart.
     */
    public static class AdditionalThreadParameters {
        private Set<String> deletedImages;

        public AdditionalThreadParameters() {
            this.deletedImages = new HashSet<>();
        }

        public void addDeletedImage(String deletedImageFilename) {
            deletedImages.add(deletedImageFilename);
        }

        public boolean isImageDeletedFromTheServer(String filename) {
            return deletedImages.contains(filename);
        }
    }

    public static class SaveThreadParameters {
        private Loadable loadable;
        private List<Post> postsToSave;
        private AtomicReference<DownloadRequestState> state;

        public SaveThreadParameters(
                Loadable loadable,
                List<Post> postsToSave) {
            this.loadable = loadable;
            this.postsToSave = postsToSave;
            this.state = new AtomicReference<>(DownloadRequestState.Running);
        }

        public Loadable getLoadable() {
            return loadable;
        }

        public List<Post> getPostsToSave() {
            return postsToSave;
        }

        public boolean isRunning() {
            return state.get() == DownloadRequestState.Running;
        }

        public boolean isCancelled() {
            return state.get() == DownloadRequestState.Cancelled;
        }

        public boolean isStopped() {
            return state.get() == DownloadRequestState.Stopped;
        }

        public void stop() {
            state.compareAndSet(DownloadRequestState.Running, DownloadRequestState.Stopped);
        }

        public void cancel() {
            state.compareAndSet(DownloadRequestState.Running, DownloadRequestState.Cancelled);
        }
    }

    class ImageWasAlreadyDeletedException extends Exception {
        public ImageWasAlreadyDeletedException(String fileName) {
            super("Image " + fileName + " was already deleted");
        }
    }

    class NoNewPostsToSaveException extends Exception {
        public NoNewPostsToSaveException() {
            super("No new posts left to save after filtering");
        }
    }

    class CouldNotCreateThreadDirectoryException extends Exception {
        public CouldNotCreateThreadDirectoryException(File threadSaveDir) {
            super("Could not create a directory to save the thread " +
                    "to (full path: " + threadSaveDir.getAbsolutePath() + ")");
        }
    }

    class CouldNotCreateNoMediaFile extends Exception {
        public CouldNotCreateNoMediaFile(File threadSaveDirImages) {
            super("Could not create .nomedia file in directory " + threadSaveDirImages.getAbsolutePath());
        }
    }

    class CouldNotCreateImagesDirectoryException extends Exception {
        public CouldNotCreateImagesDirectoryException(File threadSaveDirImages) {
            super("Could not create a directory to save the thread images" +
                    "to (full path: " + threadSaveDirImages.getAbsolutePath() + ")");
        }
    }

    class CouldNotCreateSpoilerImageDirectoryException extends Exception {
        public CouldNotCreateSpoilerImageDirectoryException(File boardSaveDir) {
            super("Could not create a directory to save the spoiler image " +
                    "to (full path: " + boardSaveDir.getAbsolutePath() + ")");
        }
    }

    public enum DownloadRequestState {
        Running(0),
        Cancelled(1),   // Pin is removed or both buttons (watch posts and save posts) are unpressed.
        Stopped(2);     // Save posts button is unpressed.

        private int type;

        DownloadRequestState(int type) {
            this.type = type;
        }
    }

    private static final Comparator<Post> postComparator = (o1, o2) -> Integer.compare(o1.no, o2.no);
}
