/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.saver;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.service.SavingNotification;
import com.github.adamantcheese.chan.ui.settings.base_directory.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.FileSegment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;

import static com.github.adamantcheese.chan.core.saver.ImageSaver.BundledImageSaveResult.BaseDirectoryDoesNotExist;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.BundledImageSaveResult.Ok;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.BundledImageSaveResult.UnknownError;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImageSaver {
    private static final String TAG = "ImageSaver";
    private static final String IMAGE_SAVER_THREAD_NAME_FORMAT = "ImageSaverThread-%d";
    /**
     * We don't want to process all images at once because that will freeze the phone. Also we don't
     * want to process images one by one because it will be way too slow. So we use this parameter
     * for maximum amount of images processed concurrently.
     */
    private static final int CONCURRENT_REQUESTS_COUNT = 4;
    /**
     * We use unbounded queue and this variable is it's initial capacity.
     */
    private static final int UNBOUNDED_QUEUE_MIN_CAPACITY = 32;
    /**
     * Since FileCacheV2 is fully asynchronous we can just use a single thread to enqueue requests.
     */
    private static final int THREADS_COUNT = 1;

    /**
     * Amount of successfully downloaded images
     */
    private AtomicInteger doneTasks = new AtomicInteger(0);
    /**
     * Total amount of images in a batch to download
     */
    private AtomicInteger totalTasks = new AtomicInteger(0);
    /**
     * Amount of images we couldn't download
     */
    private AtomicInteger failedTasks = new AtomicInteger(0);
    /**
     * Callbacks to show and update the LoadingViewController with the album download information
     * (downloaded/total to download/failed to download images)
     */
    @Nullable
    private BundledDownloadTaskCallbacks callbacks;

    private FileManager fileManager;
    /**
     * Like a normal toast but automatically cancels previous toast when showing a new one to avoid
     * toast spam.
     */
    private CancellableToast cancellableToast = new CancellableToast();
    /**
     * Reactive queue used for batch image downloads.
     */
    private FlowableProcessor<ImageSaveTask> imageSaverQueue = PublishProcessor.create();

    /**
     * Id of a thread in the workerScheduler
     */
    private AtomicInteger imagesSaverThreadIndex = new AtomicInteger(0);
    /**
     * Only for batch downloads. Holds the urls of all images in a batch. Use for batch canceling.
     * If an image url is not present in the activeDownloads that means that it was canceled.
     */
    @GuardedBy("itself")
    private final Set<String> activeDownloads = new HashSet<>(64);

    private Scheduler workerScheduler = Schedulers.from(Executors.newFixedThreadPool(THREADS_COUNT, r -> new Thread(r,
            String.format(Locale.US, IMAGE_SAVER_THREAD_NAME_FORMAT, imagesSaverThreadIndex.getAndIncrement())
    )));

    /**
     * This is a singleton class so we don't care about the disposable since we will never should
     * dispose of this stream
     */
    @SuppressLint("CheckResult")
    public ImageSaver(FileManager fileManager) {
        this.fileManager = fileManager;
        EventBus.getDefault().register(this);

        imageSaverQueue.observeOn(workerScheduler)
                // Unbounded queue
                .onBackpressureBuffer(UNBOUNDED_QUEUE_MIN_CAPACITY, false, true).flatMapSingle((t) -> {
            return Single.just(t)
                    .observeOn(workerScheduler)
                    .flatMap((task) -> {
                        boolean isStillActive = false;

                        synchronized (activeDownloads) {
                            isStillActive = activeDownloads.contains(task.getPostImageUrl());
                        }

                        // If the download is not present in activeDownloads that means that
                        // it wat canceled, so exit immediately
                        if (!isStillActive) {
                            return Single.just(BundledDownloadResult.Canceled);
                        }

                        return task.run();
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((error) -> imageSaveTaskFailed(t, error))
                    .doOnSuccess((success) -> imageSaveTaskFinished(t, success))
                    .doOnError((error) -> Logger.e(TAG, "Unhandled exception", error))
                    .onErrorReturnItem(BundledDownloadResult.Failure);
        }, false, CONCURRENT_REQUESTS_COUNT).subscribe((result) -> {
            // Do nothing
        }, (error) -> {
            throw new RuntimeException(TAG + " Uncaught exception!!! " + "workerQueue is in error state now!!! "
                    + "This should not happen!!!, original error = " + error.getMessage());
        }, () -> {
            throw new RuntimeException(TAG + " workerQueue stream has completed!!! This should not happen!!!");
        });
    }

    public void startDownloadTask(Context context, final ImageSaveTask task, DownloadTaskCallbacks callbacks) {
        if (hasPermission(context)) {
            startDownloadTaskInternal(task, callbacks);
            return;
        }

        requestPermission(context, granted -> {
            if (!granted) {
                callbacks.onError(context.getString(R.string.image_saver_no_write_permission_message));
                return;
            }

            startDownloadTaskInternal(task, callbacks);
        });
    }

    private void startDownloadTaskInternal(ImageSaveTask task, DownloadTaskCallbacks callbacks) {
        if (!fileManager.baseDirectoryExists(SavedFilesBaseDirectory.class)) {
            callbacks.onError(getString(R.string.files_base_dir_does_not_exist));
            return;
        }

        AbstractFile saveLocation = getSaveLocation(task);
        if (saveLocation == null) {
            callbacks.onError(getString(R.string.image_saver_could_not_figure_out_save_location));
            return;
        }

        PostImage postImage = task.getPostImage();
        task.setDestination(deduplicateFile(postImage, task));

        // At this point we already have disk permissions
        startTask(task);
        updateNotification();
    }

    public BundledImageSaveResult startBundledTask(Context context, final List<ImageSaveTask> tasks) {
        if (!fileManager.baseDirectoryExists(SavedFilesBaseDirectory.class)) {
            return BaseDirectoryDoesNotExist;
        }

        if (hasPermission(context)) {
            boolean result = startBundledTaskInternal(tasks);
            return result ? Ok : UnknownError;
        }

        // This does not request the permission when another request is pending.
        // This is ok and will drop the tasks.
        requestPermission(context, granted -> {
            if (granted) {
                if (startBundledTaskInternal(tasks)) {
                    return;
                }
            }

            String text = getText(null, false, false);
            cancellableToast.showToast(text, Toast.LENGTH_LONG);
        });

        return Ok;
    }

    @Nullable
    public AbstractFile getSaveLocation(ImageSaveTask task) {
        AbstractFile baseSaveDir = fileManager.newBaseDirectoryFile(SavedFilesBaseDirectory.class);
        if (baseSaveDir == null) {
            Logger.e(TAG, "getSaveLocation() fileManager.newSaveLocationFile() returned null");
            return null;
        }

        AbstractFile createdBaseSaveDir = fileManager.create(baseSaveDir);

        if (!fileManager.exists(baseSaveDir) || createdBaseSaveDir == null) {
            Logger.e(TAG, "Couldn't create base image save directory");
            return null;
        }

        if (!fileManager.baseDirectoryExists(SavedFilesBaseDirectory.class)) {
            Logger.e(TAG, "Base save local directory does not exist");
            return null;
        }

        String subFolder = task.getSubFolder();
        if (subFolder != null) {
            return baseSaveDir.cloneUnsafe(subFolder);
        }

        return baseSaveDir;
    }

    public void imageSaveTaskFailed(ImageSaveTask task, Throwable error) {
        BackgroundUtils.ensureMainThread();
        failedTasks.incrementAndGet();

        synchronized (activeDownloads) {
            activeDownloads.remove(task.getPostImageUrl());
        }

        if (callbacks != null) {
            callbacks.onImageProcessed(doneTasks.get(), failedTasks.get(), totalTasks.get());
        }

        if (checkBatchCompleted()) {
            onBatchCompleted();
        }

        Logger.e(TAG, "imageSaveTaskFailed imageUrl = " + task.getPostImageUrl());

        String errorMessage = getString(R.string.image_saver_failed_to_save_image, error.getMessage());
        cancellableToast.showToast(errorMessage, Toast.LENGTH_LONG);
    }

    public void imageSaveTaskFinished(ImageSaveTask task, BundledDownloadResult result) {
        BackgroundUtils.ensureMainThread();
        doneTasks.incrementAndGet();

        synchronized (activeDownloads) {
            activeDownloads.remove(task.getPostImageUrl());
        }

        if (callbacks != null) {
            callbacks.onImageProcessed(doneTasks.get(), failedTasks.get(), totalTasks.get());
        }

        Logger.d(TAG, "imageSaveTaskFinished imageUrl = " + task.getPostImageUrl());
        boolean wasAlbumSave = false;

        if (checkBatchCompleted()) {
            wasAlbumSave = totalTasks.get() > 1 && failedTasks.get() == 0;
            onBatchCompleted();
        }

        updateNotification();

        // Do not show the toast when image download has failed; we will show it in imageSaveTaskFailed
        if (result == BundledDownloadResult.Success) {
            String text = getText(task, true, wasAlbumSave);
            cancellableToast.showToast(text, Toast.LENGTH_LONG);
        } else if (result == BundledDownloadResult.Canceled) {
            cancellableToast.showToast(R.string.image_saver_canceled_by_user_message, Toast.LENGTH_LONG);
        }
    }

    private boolean checkBatchCompleted() {
        if (doneTasks.get() + failedTasks.get() > totalTasks.get()) {
            throw new IllegalStateException(
                    "Amount of downloaded and failed tasks is greater than total! " + "(done = " + doneTasks.get()
                            + ", failed = " + failedTasks.get() + ", total = " + totalTasks.get() + ")");
        }

        return doneTasks.get() + failedTasks.get() == totalTasks.get();
    }

    private void onBatchCompleted() {
        BackgroundUtils.ensureMainThread();
        Logger.d(TAG,
                "onBatchCompleted " + "downloaded = " + doneTasks.get() + ", " + "failed = " + failedTasks.get() + ", "
                        + "total = " + totalTasks.get()
        );

        totalTasks.set(0);
        doneTasks.set(0);
        failedTasks.set(0);

        updateNotification();

        if (callbacks != null) {
            callbacks.onBundleDownloadCompleted();
        }
    }

    @Subscribe
    public void onEvent(SavingNotification.SavingCancelRequestMessage message) {
        cancelAll();
    }

    private boolean startBundledTaskInternal(List<ImageSaveTask> tasks) {
        boolean allSuccess = true;

        for (ImageSaveTask task : tasks) {
            PostImage postImage = task.getPostImage();

            AbstractFile deduplicateFile = deduplicateFile(postImage, task);
            if (deduplicateFile == null) {
                allSuccess = false;
                continue;
            }

            task.setDestination(deduplicateFile);
            startTask(task);
        }

        updateNotification();
        return allSuccess;
    }

    private void startTask(ImageSaveTask task) {
        synchronized (activeDownloads) {
            activeDownloads.add(task.getPostImageUrl());
        }

        totalTasks.incrementAndGet();
        imageSaverQueue.onNext(task);
    }

    private void cancelAll() {
        synchronized (activeDownloads) {
            activeDownloads.clear();
        }

        updateNotification();

        cancellableToast.showToast(R.string.image_saver_canceled_by_user_message, Toast.LENGTH_LONG);
    }

    private void updateNotification() {
        Intent service = new Intent(getAppContext(), SavingNotification.class);
        if (totalTasks.get() == 0) {
            getAppContext().stopService(service);
        } else {
            service.putExtra(SavingNotification.DONE_TASKS_KEY, doneTasks.get());
            service.putExtra(SavingNotification.TOTAL_TASKS_KEY, totalTasks.get());
            getAppContext().startService(service);
        }
    }

    private String getText(ImageSaveTask task, boolean success, boolean wasAlbumSave) {
        String text;
        if (success) {
            if (wasAlbumSave) {
                String location;
                AbstractFile locationFile = getSaveLocation(task);

                if (locationFile == null) {
                    location = getString(R.string.image_saver_unknown_location_message);
                } else {
                    location = locationFile.getFullPath();
                }

                text = getString(R.string.image_saver_album_download_success, location);
            } else {
                text = getString(R.string.image_saver_saved_as_message, fileManager.getName(task.getDestination()));
            }
        } else {
            text = getString(R.string.image_saver_failed_to_save_image_message);
        }

        return text;
    }

    /**
     * @param isFileName is used to figure out what characters are allowed and what are not.
     *                   If set to false, then we additionally remove all '.' characters because
     *                   directory names should not have '.' characters (well they actually can but
     *                   let's filter them anyway). If it's false then it is implied that the "name"
     *                   param is a directory segment name.
     */
    private String filterName(String name, boolean isFileName) {
        String filteredName;

        if (isFileName) {
            filteredName = StringUtils.fileNameRemoveBadCharacters(name);
        } else {
            filteredName = StringUtils.dirNameRemoveBadCharacters(name);
        }

        String extension = StringUtils.extractFileNameExtension(filteredName);

        // Remove the extension length + the '.' symbol from the resulting "filteredName" length
        // and if it equals to 0 that means that the whole file name consists of bad characters
        // (e.g. the whole filename consists of japanese characters) so we need to generate a new
        // file name
        boolean isOnlyExtensionLeft = (extension != null && (filteredName.length() - extension.length() - 1) == 0);

        // filteredName.length() == 0 will only be true when "name" parameter does not have an
        // extension
        if (filteredName.length() == 0 || isOnlyExtensionLeft) {
            String appendExtension;

            if (extension != null) {
                // extractFileNameExtension returns an extension without the '.' symbol
                appendExtension = "." + extension;
            } else {
                appendExtension = "";
            }

            filteredName = System.currentTimeMillis() + appendExtension;
        }

        return filteredName;
    }

    @Nullable
    private AbstractFile deduplicateFile(PostImage postImage, ImageSaveTask task) {
        String name = ChanSettings.saveServerFilename.get() ? postImage.serverFilename : postImage.filename;

        //dedupe shared files to have their own file name; ok to overwrite, prevents lots of downloads for multiple shares
        String fileName = filterName(name + (task.getShare() ? "_shared" : "") + "." + postImage.extension, true);

        AbstractFile saveLocation = getSaveLocation(task);
        if (saveLocation == null) {
            Logger.e(TAG, "Save location is null!");
            return null;
        }

        AbstractFile saveFile = saveLocation.clone(new FileSegment(fileName));

        //shared files don't need deduplicating
        while (fileManager.exists(saveFile) && !task.getShare()) {
            String currentTimeHash = Long.toString(SystemClock.elapsedRealtimeNanos(), Character.MAX_RADIX);
            String resultFileName = name + "_" + currentTimeHash + "." + postImage.extension;

            fileName = filterName(resultFileName, true);
            saveFile = saveLocation.clone(new FileSegment(fileName));
        }

        return saveFile;
    }

    private boolean hasPermission(Context context) {
        return ((StartActivity) context).getRuntimePermissionsHelper()
                .hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission(Context context, RuntimePermissionsHelper.Callback callback) {
        ((StartActivity) context).getRuntimePermissionsHelper()
                .requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback);
    }

    public void setBundledTaskCallback(BundledDownloadTaskCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void removeBundleTaskCallback() {
        this.callbacks = null;
    }

    public enum BundledDownloadResult {
        Success,
        Failure,
        Canceled
    }

    public enum BundledImageSaveResult {
        Ok,
        BaseDirectoryDoesNotExist,
        UnknownError
    }

    public interface DownloadTaskCallbacks {
        void onError(String message);
    }

    public interface BundledDownloadTaskCallbacks {
        void onImageProcessed(int downloaded, int failed, int total);

        void onBundleDownloadCompleted();
    }
}
