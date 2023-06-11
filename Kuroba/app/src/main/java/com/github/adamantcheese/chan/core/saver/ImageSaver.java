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

import static com.github.adamantcheese.chan.core.saver.ImageSaver.ImageSaveResult.BaseDirectoryDoesNotExist;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.ImageSaveResult.NoWriteExternalStoragePermission;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.ImageSaveResult.Saved;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.ImageSaveResult.UnknownError;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.TaskResult.Canceled;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.TaskResult.Success;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.features.notifications.SavingNotification;
import com.github.adamantcheese.chan.ui.settings.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.*;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.FileSegment;
import com.google.common.io.Files;

import org.greenrobot.eventbus.*;

import java.io.File;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import okhttp3.HttpUrl;

public class ImageSaver {
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
     * Amount of successfully downloaded images
     */
    private final AtomicInteger doneTasks = new AtomicInteger(0);
    /**
     * Total amount of images in a batch to download
     */
    private final AtomicInteger totalTasks = new AtomicInteger(0);
    /**
     * Amount of images we couldn't download
     */
    private final AtomicInteger failedTasks = new AtomicInteger(0);

    private final FileManager fileManager;

    /**
     * Reactive queue used for batch image downloads.
     */
    private final FlowableProcessor<ImageSaveTask> imageSaverQueue = PublishProcessor.create();

    /**
     * Only for batch downloads. Holds the urls of all images in a batch. Use for batch canceling.
     * If an image url is not present in the activeDownloads that means that it was canceled.
     */
    @GuardedBy("itself")
    private final Set<HttpUrl> activeDownloads = new HashSet<>(64);

    private final Scheduler workerScheduler = Schedulers.from(new ForkJoinPool(1));

    /**
     * This is a singleton class so we don't care about the disposable since we will never should
     * dispose of this stream
     */
    @SuppressLint("CheckResult")
    public ImageSaver(FileManager fileManager) {
        this.fileManager = fileManager;
        EventBus.getDefault().register(this);

        imageSaverQueue
                // Unbounded queue
                .onBackpressureBuffer(UNBOUNDED_QUEUE_MIN_CAPACITY, false, true)
                .observeOn(workerScheduler)
                .flatMapSingle((t) -> Single
                        .just(t)
                        .observeOn(workerScheduler)
                        .flatMap((task) -> {
                            synchronized (activeDownloads) {
                                boolean isStillActive = activeDownloads.contains(task.postImage.imageUrl);

                                // If the download is not present in activeDownloads that means that
                                // it wat canceled, so exit immediately
                                if (!isStillActive) {
                                    return Single.just(Canceled);
                                }
                            }

                            return task.run();
                        })
                        .observeOn(AndroidSchedulers.mainThread())
                        .doOnError((error) -> imageSaveTaskFailed(t, error))
                        .doOnSuccess((success) -> imageSaveTaskFinished(t, success))
                        .doOnError((error) -> Logger.w(ImageSaver.this, "Unhandled exception", error))
                        .onErrorReturnItem(TaskResult.Failure), false, CONCURRENT_REQUESTS_COUNT)
                .subscribe((result) -> {
                    // Do nothing
                }, (error) -> {
                    throw new RuntimeException(ImageSaver.this
                            + " Uncaught exception!!! "
                            + "workerQueue is in error state now!!! "
                            + "This should not happen!!!, original error = "
                            + error.getMessage());
                }, () -> {
                    throw new RuntimeException(ImageSaver.this
                            + " workerQueue stream has completed!!! This should not happen!!!");
                });
    }

    public Single<ImageSaveResult> startBundledTask(Context context, final List<ImageSaveTask> tasks) {
        return Single.defer(() -> {
            if (!fileManager.baseDirectoryExists(SavedFilesBaseDirectory.class)) {
                // If current base dir is File API backed and it's not set, attempt to create it
                // manually
                if (ChanSettings.saveLocation.isFileDirActive()) {
                    File baseDirFile = new File(ChanSettings.saveLocation.getFileApiBaseDir().get());
                    if (!baseDirFile.exists() && !baseDirFile.mkdirs()) {
                        return Single.just(BaseDirectoryDoesNotExist);
                    }
                }
            }

            return checkPermission(context).flatMap((granted) -> {
                if (!granted) {
                    return Single.just(NoWriteExternalStoragePermission);
                }

                return startBundledTaskInternal(tasks).map((result) -> result ? Saved : UnknownError);
            });
        });
    }

    private Single<Boolean> checkPermission(Context context) {
        if (hasPermission(context)) {
            return Single.just(true);
        }

        return Single
                .<Boolean>create((emitter) -> requestPermission(context, emitter::onSuccess))
                .subscribeOn(AndroidSchedulers.mainThread());
    }

    private void imageSaveTaskFailed(ImageSaveTask task, Throwable error) {
        BackgroundUtils.ensureMainThread();
        failedTasks.incrementAndGet();

        synchronized (activeDownloads) {
            activeDownloads.remove(task.postImage.imageUrl);
        }

        if (checkBatchCompleted()) {
            onBatchCompleted();
        }

        Logger.w(this, "imageSaveTaskFailed imageUrl = " + task.postImage.imageUrl);

        String errorMessage = getString(R.string.image_saver_failed_to_save_image, error.getMessage());
        EventBus.getDefault().post(new StartActivity.ActivityToastMessage(errorMessage, Toast.LENGTH_LONG));
    }

    private void imageSaveTaskFinished(ImageSaveTask task, TaskResult result) {
        BackgroundUtils.ensureMainThread();
        doneTasks.incrementAndGet();

        synchronized (activeDownloads) {
            activeDownloads.remove(task.postImage.imageUrl);
        }

        Logger.vd(this, "imageSaveTaskFinished imageUrl = " + task.postImage.imageUrl);
        boolean wasAlbumSave = totalTasks.get() > 1;

        if (checkBatchCompleted()) {
            onBatchCompleted();
        } else {
            updateNotification();
        }

        // Do not show the toast when image download has failed; we will show it in imageSaveTaskFailed
        // Also don't show the toast if the task was a share, or if this is an album save task
        if (result == Success && !task.share) {
            if (totalTasks.get() == 0) {
                EventBus
                        .getDefault()
                        .post(new StartActivity.ActivityToastMessage(getText(task, wasAlbumSave), Toast.LENGTH_LONG));
            }
        } else if (result == Canceled && totalTasks.get() == 0) {
            EventBus
                    .getDefault()
                    .post(new StartActivity.ActivityToastMessage(getString(R.string.image_saver_canceled_by_user),
                            Toast.LENGTH_LONG
                    ));
        }
    }

    private boolean checkBatchCompleted() {
        return doneTasks.get() + failedTasks.get() >= totalTasks.get();
    }

    private void onBatchCompleted() {
        totalTasks.set(0);
        doneTasks.set(0);
        failedTasks.set(0);

        updateNotification();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(SavingNotification.SavingCancelRequestMessage message) {
        synchronized (activeDownloads) {
            activeDownloads.clear();
        }

        onBatchCompleted();
    }

    /**
     * We really need to run this thing on a background thread because all the file-checks may take
     * a lot of times (and ANR the app) if the base directory uses SAF and there a lot of files in
     * the album
     */
    private Single<Boolean> startBundledTaskInternal(List<ImageSaveTask> tasks) {
        return Single
                .fromCallable(() -> {
                    BackgroundUtils.ensureBackgroundThread();
                    boolean allSuccess = true;
                    boolean isAlbumSave = tasks.size() > 1;

                    for (ImageSaveTask task : tasks) {
                        AbstractFile saveLocation = task.getSaveLocation();
                        if (saveLocation == null) {
                            allSuccess = false;
                            continue;
                        }

                        task.setFinalSaveLocation(deduplicateFile(task.postImage, task, saveLocation, isAlbumSave));
                        startTask(task);
                    }

                    return allSuccess;
                })
                .subscribeOn(workerScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((event, throwable) -> updateNotification());
    }

    private void startTask(ImageSaveTask task) {
        synchronized (activeDownloads) {
            activeDownloads.add(task.postImage.imageUrl);
        }

        totalTasks.incrementAndGet();
        imageSaverQueue.onNext(task);
    }

    private void updateNotification() {
        BackgroundUtils.ensureMainThread();

        Intent service = new Intent(getAppContext(), SavingNotification.class);
        if (totalTasks.get() == 0) {
            getAppContext().stopService(service);
        } else {
            if (BackgroundUtils.isInForeground()) {
                service.putExtra(SavingNotification.DONE_TASKS_KEY, doneTasks.get());
                service.putExtra(SavingNotification.FAILED_TASKS_KEY, failedTasks.get());
                service.putExtra(SavingNotification.TOTAL_TASKS_KEY, totalTasks.get());
                ContextCompat.startForegroundService(getAppContext(), service);
            } else {
                getAppContext().stopService(service);
            }
        }
    }

    private String getText(ImageSaveTask task, boolean wasAlbumSave) {
        BackgroundUtils.ensureMainThread();

        String text;
        if (wasAlbumSave) {
            String location;
            AbstractFile saveLocation = task.getSaveLocation();

            if (saveLocation == null) {
                location = getString(R.string.image_saver_unknown_location);
            } else {
                location = saveLocation.getFullPath();
            }

            try {
                text = getString(R.string.image_saver_album_download_success, URLDecoder.decode(location, "UTF-8"));
            } catch (Exception e) {
                text = getString(R.string.image_saver_album_download_success, location);
            }
        } else {
            text = getString(R.string.image_saver_saved_as_message, task.getSavedName());
        }

        return text;
    }

    private String filterName(String name) {
        String filteredName = StringUtils.fileNameRemoveBadCharacters(name);

        String extension = Files.getFileExtension(filteredName);

        // Remove the extension length + the '.' symbol from the resulting "filteredName" length
        // and if it equals to 0 that means that the whole file name consists of bad characters
        // (e.g. the whole filename consists of japanese characters) so we need to generate a new
        // file name
        boolean isOnlyExtensionLeft = (filteredName.length() - extension.length() - 1) == 0;

        // filteredName.length() == 0 will only be true when "name" parameter does not have an
        // extension
        if (filteredName.isEmpty() || isOnlyExtensionLeft) {
            String appendExtension = !extension.isEmpty() ? "." + extension : "";
            filteredName = System.currentTimeMillis() + appendExtension;
        }

        return filteredName;
    }

    @NonNull
    private AbstractFile deduplicateFile(
            PostImage postImage, ImageSaveTask task, @NonNull AbstractFile saveLocation, boolean albumSave
    ) {
        String name = ChanSettings.saveServerFilename.get() ? postImage.serverFilename : postImage.filename;

        // get the file representing the image we're going to be saving
        String fileName = filterName(name + "." + postImage.extension);
        AbstractFile saveFile = saveLocation.clone(new FileSegment(fileName));

        //shared files don't need deduplicating, nor do album saves as we don't want to save duplicates for album saves
        int i = 1;
        while (!task.share && !albumSave && fileManager.exists(saveFile)) {
            String resultFileName = name + "_(" + i + ")." + postImage.extension;

            fileName = filterName(resultFileName);
            saveFile = saveLocation.clone(new FileSegment(fileName));
            i++;
        }

        return saveFile;
    }

    private boolean hasPermission(Context context) {
        return ((StartActivity) context)
                .getRuntimePermissionsHelper()
                .hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission(Context context, RuntimePermissionsHelper.Callback callback) {
        ((StartActivity) context)
                .getRuntimePermissionsHelper()
                .requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback);
    }

    public enum TaskResult {
        Success,
        Failure,
        Canceled
    }

    public enum ImageSaveResult {
        Saved,
        BaseDirectoryDoesNotExist,
        NoWriteExternalStoragePermission,
        UnknownError
    }

    public static class DefaultImageSaveResultEvent {
        public static void onResultEvent(Context context, ImageSaver.ImageSaveResult result) {
            switch (result) {
                case BaseDirectoryDoesNotExist:
                    showToast(context, R.string.files_base_dir_does_not_exist);
                    break;
                case NoWriteExternalStoragePermission:
                    showToast(context, R.string.could_not_start_saving_no_permissions);
                    break;
                case UnknownError:
                    showToast(context, R.string.album_download_could_not_save_one_or_more_images);
                    break;
                case Saved:
                    // Do nothing, we got the permissions and started downloading an image
                    break;
            }
        }
    }
}
