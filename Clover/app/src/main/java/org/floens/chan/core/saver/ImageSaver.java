/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.saver;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import android.widget.Toast;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.cache.FileCacheListener;
import org.floens.chan.core.cache.FileCacheProvider;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.storage.Storage;
import org.floens.chan.core.storage.StorageFile;
import org.floens.chan.ui.activity.RuntimePermissionsHelper;
import org.floens.chan.ui.service.SavingNotification;
import org.floens.chan.utils.AndroidUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

import static org.floens.chan.core.storage.Storage.filterName;
import static org.floens.chan.utils.AndroidUtils.getAppContext;
import static org.floens.chan.utils.AndroidUtils.getString;

@Singleton
public class ImageSaver implements ImageSaveTask.ImageSaveTaskCallback {
    private static final String TAG = "ImageSaver";
    private static final int NOTIFICATION_ID = 3;
    private static final int MAX_NAME_LENGTH = 50;
    private NotificationManager notificationManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private int doneTasks = 0;
    private int totalTasks = 0;
    private Toast toast;

    private Storage storage;
    private FileCache fileCache;

    @Inject
    public ImageSaver(Storage storage, FileCache fileCache) {
        this.storage = storage;
        this.fileCache = fileCache;

        EventBus.getDefault().register(this);
        notificationManager = (NotificationManager) getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void onEvent(SavingNotification.SavingCancelRequestMessage message) {
        cancelAll();
    }

    public String getSafeNameForFolder(String name) {
        String filtered = filterName(name);
        return filtered.substring(0, Math.min(filtered.length(), MAX_NAME_LENGTH));
    }

    public void share(PostImage postImage) {
        fileCache.downloadFile(postImage.imageUrl.toString(), new FileCacheListener() {
            @Override
            public void onSuccess(File file) {
                shareFileCacheImage(postImage, file);
            }
        });
    }

    private void shareFileCacheImage(PostImage postImage, File file) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setAction(Intent.ACTION_SEND);
        Uri fileUri = FileCacheProvider.getUriForFile(file);
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        if (postImage.type == PostImage.Type.MOVIE) {
            intent.setType("video/*");
        } else {
            intent.setType("image/" + postImage.extension);
        }

        AndroidUtils.openIntent(Intent.createChooser(intent, null));
    }

    public void addTask(final ImageSaveTask task) {
        addTasks(Collections.singletonList(task), null, null);
    }

    public void addTasks(final List<ImageSaveTask> tasks, final String folder, Runnable success) {
        storage.prepareForSave(folder, () -> {
            if (!needsRequestExternalStoragePermission()) {
                queueTasks(tasks, folder);
                if (success != null) {
                    success.run();
                }
            } else {
                requestPermission(granted -> {
                    if (granted) {
                        queueTasks(tasks, folder);
                    } else {
                        showStatusToast(null, false);
                    }
                });
            }
        });
    }

    private void queueTasks(final List<ImageSaveTask> tasks, final String folder) {
        totalTasks = 0;

        for (ImageSaveTask task : tasks) {
            PostImage postImage = task.getPostImage();
            String name = ChanSettings.saveOriginalFilename.get() ? postImage.originalName : postImage.filename;

            StorageFile file;
            if (storage.mode() == Storage.Mode.FILE) {
                file = storage.obtainLegacyStorageFileForName(folder, name + "." + postImage.extension);
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    throw new IllegalStateException();
                }
                file = storage.obtainStorageFileForName(folder, name + "." + postImage.extension);
            }
            if (file == null) {
                throw new IllegalStateException("Failed to obtain a StorageFile");
            }
            task.setDestination(file);
            task.setShowToast(tasks.size() == 1);

            task.setCallback(this);

            totalTasks++;
            executor.execute(task);
        }

        updateNotification();
    }

    @Override
    public void imageSaveTaskFinished(ImageSaveTask task, boolean success) {
        doneTasks++;
        if (doneTasks == totalTasks) {
            totalTasks = 0;
            doneTasks = 0;
        }
        updateNotification();

        if (task.isMakeBitmap()) {
            showImageSaved(task);
        }
        if (task.isShowToast()) {
            showStatusToast(task, success);
        }
    }

    private void cancelAll() {
        executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();

        totalTasks = 0;
        doneTasks = 0;
        updateNotification();
    }

    private void updateNotification() {
        Intent service = new Intent(getAppContext(), SavingNotification.class);
        if (totalTasks == 0) {
            getAppContext().stopService(service);
        } else {
            service.putExtra(SavingNotification.DONE_TASKS_KEY, doneTasks);
            service.putExtra(SavingNotification.TOTAL_TASKS_KEY, totalTasks);
            getAppContext().startService(service);
        }
    }

    private void showImageSaved(ImageSaveTask task) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext());
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setContentTitle(getString(R.string.image_save_saved));
        String savedAs = getAppContext().getString(R.string.image_save_as, task.getDestination().name());
        builder.setContentText(savedAs);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(task.getBitmap())
                .setSummaryText(savedAs));

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showStatusToast(ImageSaveTask task, boolean success) {
        if (toast != null) {
            toast.cancel();
        }

        String text = success ?
                getAppContext().getString(R.string.image_save_as, task.getDestination().name()) :
                getString(R.string.image_save_failed);
        toast = Toast.makeText(getAppContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }

    private boolean needsRequestExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return false;
        }

        return !Chan.getInstance().getRuntimePermissionsHelper().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission(RuntimePermissionsHelper.Callback callback) {
        Chan.getInstance().getRuntimePermissionsHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback);
    }
}
