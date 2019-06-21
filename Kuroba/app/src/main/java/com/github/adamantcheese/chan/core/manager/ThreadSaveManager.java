package com.github.adamantcheese.chan.core.manager;

import android.annotation.SuppressLint;
import android.util.Pair;

import androidx.annotation.GuardedBy;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @GuardedBy("itself")
    private final Map<Loadable, SaveThreadParameters> activeDownloads = new HashMap<>();

    private OkHttpClient okHttpClient = new OkHttpClient()
            .newBuilder()
            .writeTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(OKHTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors());

    private PublishProcessor<Loadable> workerQueue = PublishProcessor.create();

    // TODO: get permissions
    // TODO: don't use postImage extension for thumbnail (they are probably either jpegs or pngs)
    // TODO: check whenther pin watcher is on or off before start saving a thread
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

        initRxWorkerQueue();
    }

    /**
     * Initializes main rx queue that is going to accept new downloading requests and process them
     * sequentially.
     *
     * This class is a singleton so we don't really care about disposing of the rx streams
     * */
    @SuppressLint("CheckResult")
    private void initRxWorkerQueue() {
        workerQueue.concatMapSingle((loadable) -> {
            SaveThreadParameters parameters;

            synchronized (activeDownloads) {
                Logger.d(TAG, "New downloading request started, activeDownloads count = " + activeDownloads.size());
                parameters = activeDownloads.get(loadable);
            }

            if (parameters == null) {
                throw new ParametersNotFoundException(loadable);
            }

            return saveThreadInternal(loadable, parameters.getPostsToSave())
                    .subscribeOn(Schedulers.from(executorService))
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((error) -> onDownloadingError(error, loadable))
                    .doOnSuccess((result) -> onDownloadingCompleted(result, loadable))
                    .doOnEvent((result, error) -> Logger.d(TAG, "Downloading request has completed, activeDownloads count = " + activeDownloads.size()))
                    // Suppress all of the exceptions so that the stream does not complete
                    .onErrorReturnItem(false);
        }).subscribe(
                (res) -> {},
                (error) -> Logger.e(TAG, "Uncaught exception!!!", error),
                () -> Logger.e(TAG, "BAD!!! workerQueue stream has completed!!! This should happen!!!"));
    }

    /**
     * Enqueues a thread's posts with all the images/webm/etc to be saved to the disk.
     *
     * @param downloadingCallback is used for when user clicks "save thread" button to show downloading progress.
     * Callback may be removed at any time during the download.
     * @param resultCallback is used for delivering the result of a download.
     * */
    public void enqueueThreadToSave(
            Loadable loadable,
            List<Post> postsToSave,
            ResultCallback resultCallback,
            @Nullable DownloadingCallback downloadingCallback) {

        synchronized (activeDownloads) {
            if (activeDownloads.containsKey(loadable)) {
                Logger.d(TAG, "Downloader is already running for " + loadable);
                return;
            }
        }

        // We don't want to have two downloading callbacks set at the same time.
        // We assume that user can't start prefetching two different threads at the same time.
        if (downloadingCallback != null) {
            if (isDownloadingCallbackAlreadyAdded()) {
                throw new RuntimeException("Cannot have more than one prefetch download at a time!");
            }
        }

        SaveThreadParameters parameters = new SaveThreadParameters(
                loadable,
                postsToSave,
                resultCallback,
                downloadingCallback);

        // Store the parameter of this download
        synchronized (activeDownloads) {
            activeDownloads.put(loadable, parameters);
        }

        // Enqueue the download
        workerQueue.onNext(loadable);
    }

    public void removeCallbacks(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters parameters = activeDownloads.get(loadable);
            if (parameters == null) {
                return;
            }

            parameters.removeCallbacks();
        }
    }

    private void onDownloadingCompleted(boolean result, Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters saveThreadParameters = activeDownloads.get(loadable);
            if (saveThreadParameters == null) {
                return;
            }

            AtomicReference<ResultCallback> resultCallbackRef = saveThreadParameters.getResultCallback();
            if (resultCallbackRef != null) {
                ResultCallback resultCallback = resultCallbackRef.get();
                if (resultCallback != null) {
                    resultCallback.onResult(result);
                }
            }

            saveThreadParameters.removeCallbacks();
            activeDownloads.remove(loadable);
        }
    }

    private void onDownloadingError(Throwable error, Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters saveThreadParameters = activeDownloads.get(loadable);
            if (saveThreadParameters == null) {
                return;
            }

            AtomicReference<ResultCallback> resultCallbackRef = saveThreadParameters.getResultCallback();
            if (resultCallbackRef != null) {
                ResultCallback resultCallback = resultCallbackRef.get();
                if (resultCallback != null) {
                    if (isFatalException(error)) {
                        resultCallback.onError(error);
                    }
                }
            }

            saveThreadParameters.removeCallbacks();
            activeDownloads.remove(loadable);
        }
    }

    private boolean isDownloadingCallbackAlreadyAdded() {
        synchronized (activeDownloads) {
            for (Map.Entry<Loadable, SaveThreadParameters> entry : activeDownloads.entrySet()) {
                if (entry.getValue().getDownloadingCallback().get() != null) {
                    return true;
                }
            }
        }

        return false;
    }

    // TODO: We should load images before post filtering, because if an image was not downloaded
    //  because of an error it won't be re downloaded next time since the post with the image will
    //  be filtered

    /**
     * Saves provided posts to a json file called "thread.json". Also saved all of the files that the
     * posts contain.
     *
     * @param loadable is a unique identifier of a thread we are saving.
     * @param postsToSave posts of a thread to be saved.
     *
     * @return false when there are no new posts to save (we already have of the provided posts saved)
     * and true when there were new posts to save and we have successfully saved them.
     * */
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
                throw new NoNewPostsToSaveException();
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

            sendStartEvent(loadable);

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
                                        loadable,
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
                sendEndEvent(loadable);
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
            Loadable loadable,
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
                        sendProgressEvent(
                                loadable,
                                currentImageDownloadIndex,
                                postsWithImagesCount);
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

    /**
     * Sends start event to the callback that renders LoadingView. This event will show the LoadingView.
     * */
    private void sendStartEvent(Loadable loadable) {
        DownloadingCallback callback = getDownloadingCallback(loadable);
        if (callback != null) {
            Logger.d(TAG, "Started downloading thread");
            callback.onStartedDownloading();
        }
    }

    /**
     * Sends progress event to the callback that renders LoadingView.
     * This event will advance the progress bar.
     * */
    private void sendProgressEvent(
            Loadable loadable,
            AtomicInteger currentImageDownloadIndex,
            int postsWithImagesCount) {
        DownloadingCallback callback = getDownloadingCallback(loadable);
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

    /**
     * Sends end event to the callback that renders LoadingView. This event will hide the LoadingView.
     * */
    private void sendEndEvent(Loadable loadable) {
        DownloadingCallback callback = getDownloadingCallback(loadable);
        if (callback != null) {
            Logger.d(TAG, "Completed downloading thread");
            callback.onCompletedDownloading();
        }
    }

    @Nullable
    private DownloadingCallback getDownloadingCallback(Loadable loadable) {
        synchronized (activeDownloads) {
            SaveThreadParameters saveThreadParameters = activeDownloads.get(loadable);
            if (saveThreadParameters == null) {
                return null;
            }

            AtomicReference<DownloadingCallback> downloadingCallbackRef =
                    saveThreadParameters.getDownloadingCallback();
            if (downloadingCallbackRef == null) {
                return null;
            }

            return downloadingCallbackRef.get();
        }
    }

    private boolean isFatalException(Throwable error) {
        if (error instanceof NoNewPostsToSaveException) {
            return false;
        }

        return true;
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

    public interface ResultCallback {
        void onResult(boolean result);
        void onError(Throwable error);
    }

    public interface DownloadingCallback {
        void onStartedDownloading();
        void onProgress(int percent);
        void onCompletedDownloading();
    }

    public static class SaveThreadParameters {
        private Loadable loadable;
        private List<Post> postsToSave;
        private AtomicReference<ResultCallback> resultCallback;
        private AtomicReference<DownloadingCallback> downloadingCallback;

        public SaveThreadParameters(
                Loadable loadable,
                List<Post> postsToSave,
                ResultCallback resultCallback,
                DownloadingCallback downloadingCallback) {
            this.loadable = loadable;
            this.postsToSave = postsToSave;
            this.resultCallback = new AtomicReference<>(resultCallback);
            this.downloadingCallback = new AtomicReference<>(downloadingCallback);
        }

        public Loadable getLoadable() {
            return loadable;
        }

        public List<Post> getPostsToSave() {
            return postsToSave;
        }

        public AtomicReference<ResultCallback> getResultCallback() {
            return resultCallback;
        }

        public AtomicReference<DownloadingCallback> getDownloadingCallback() {
            return downloadingCallback;
        }

        public void removeCallbacks() {
            resultCallback.set(null);
            downloadingCallback.set(null);
        }
    }

    class ParametersNotFoundException extends Exception {
        public ParametersNotFoundException(Loadable loadable) {
            super("Parameters not found with loadable " + loadable.toString());
        }
    }

    class NoNewPostsToSaveException extends Exception {
        public NoNewPostsToSaveException() {
            super("No new posts left to save after filtering");
        }
    }

    private static final Comparator<Post> postComparator = (o1, o2) -> Integer.compare(o1.no, o2.no);
}
