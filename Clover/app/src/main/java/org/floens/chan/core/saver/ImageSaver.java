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
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.helper.RuntimePermissionsHelper;
import org.floens.chan.ui.service.SavingNotification;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.getAppContext;
import static org.floens.chan.utils.AndroidUtils.getString;

public class ImageSaver implements ImageSaveTask.ImageSaveTaskCallback {
    private static final String TAG = "ImageSaver";
    private static final int NOTIFICATION_ID = 3;
    private static final int MAX_NAME_LENGTH = 50;
    private static final Pattern REPEATED_UNDERSCORES_PATTERN = Pattern.compile("_+");
    private static final Pattern SAFE_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9._]");
    private static final ImageSaver instance = new ImageSaver();

    private NotificationManager notificationManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private int doneTasks = 0;
    private int totalTasks = 0;
    private Toast toast;

    public static ImageSaver getInstance() {
        return instance;
    }

    private ImageSaver() {
        EventBus.getDefault().register(this);
        notificationManager = (NotificationManager) getAppContext().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void startDownloadTask(Context context, final ImageSaveTask task) {
        PostImage postImage = task.getPostImage();
        String name = ChanSettings.saveOriginalFilename.get() ? postImage.originalName : postImage.filename;
        String fileName = filterName(name + "." + postImage.extension);
        task.setDestination(findUnusedFileName(new File(getSaveLocation(), fileName), false));

//        task.setMakeBitmap(true);
        task.setShowToast(true);

        if (!hasPermission(context)) {
            // This does not request the permission when another request is pending.
            // This is ok and will drop the task.
            requestPermission(context, new RuntimePermissionsHelper.Callback() {
                @Override
                public void onRuntimePermissionResult(boolean granted) {
                    if (granted) {
                        startTask(task);
                        updateNotification();
                    } else {
                        showToast(null, false);
                    }
                }
            });
        } else {
            startTask(task);
            updateNotification();
        }
    }

    public boolean startBundledTask(Context context, final String subFolder, final List<ImageSaveTask> tasks) {
        if (!hasPermission(context)) {
            // This does not request the permission when another request is pending.
            // This is ok and will drop the tasks.
            requestPermission(context, new RuntimePermissionsHelper.Callback() {
                @Override
                public void onRuntimePermissionResult(boolean granted) {
                    if (granted) {
                        startBundledTaskInternal(subFolder, tasks);
                    } else {
                        showToast(null, false);
                    }
                }
            });
            return false;
        } else {
            startBundledTaskInternal(subFolder, tasks);
            return true;
        }
    }

    public String getSubFolder(String name) {
        String filtered = filterName(name);
        filtered = filtered.substring(0, Math.min(filtered.length(), MAX_NAME_LENGTH));
        return filtered;
    }

    public File getSaveLocation() {
        return new File(ChanSettings.saveLocation.get());
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
            showToast(task, success);
        }
    }

    public void onEvent(SavingNotification.SavingCancelRequestMessage message) {
        cancelAll();
    }

    private void startTask(ImageSaveTask task) {
        task.setCallback(this);

        totalTasks++;
        executor.execute(task);
    }

    private void startBundledTaskInternal(String subFolder, List<ImageSaveTask> tasks) {
        for (ImageSaveTask task : tasks) {
            PostImage postImage = task.getPostImage();
            String fileName = filterName(postImage.originalName + "." + postImage.extension);
            task.setDestination(new File(getSaveLocation() + File.separator + subFolder + File.separator + fileName));

            startTask(task);
        }
        updateNotification();
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
        String savedAs = getAppContext().getString(R.string.image_save_as, task.getDestination().getName());
        builder.setContentText(savedAs);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setStyle(new NotificationCompat.BigPictureStyle()
                .bigPicture(task.getBitmap())
                .setSummaryText(savedAs));

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showToast(ImageSaveTask task, boolean success) {
        if (toast != null) {
            toast.cancel();
        }

        String text = success ?
                getAppContext().getString(R.string.image_save_as, task.getDestination().getName()) :
                getString(R.string.image_save_failed);
        toast = Toast.makeText(getAppContext(), text, Toast.LENGTH_LONG);
        toast.show();
    }

    private String filterName(String name) {
        name = name.replace(' ', '_');
        name = SAFE_CHARACTERS_PATTERN.matcher(name).replaceAll("");
        name = REPEATED_UNDERSCORES_PATTERN.matcher(name).replaceAll("_");
        if (name.length() == 0) {
            name = "_";
        }
        return name;
    }

    private File findUnusedFileName(File start, boolean directory) {
        String base;
        String extension;

        if (directory) {
            base = start.getAbsolutePath();
            extension = null;
        } else {
            String[] splitted = start.getAbsolutePath().split("\\.(?=[^\\.]+$)");
            if (splitted.length == 2) {
                base = splitted[0];
                extension = "." + splitted[1];
            } else {
                base = splitted[0];
                extension = ".";
            }
        }

        File test;
        if (directory) {
            test = new File(base);
        } else {
            test = new File(base + extension);
        }

        int index = 0;
        int tries = 0;
        while (test.exists() && tries++ < 100) {
            if (directory) {
                test = new File(base + "_" + index);
            } else {
                test = new File(base + "_" + index + extension);
            }
            index++;
        }

        return test;
    }

    private boolean hasPermission(Context context) {
        return ((StartActivity) context).getRuntimePermissionsHelper().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission(Context context, RuntimePermissionsHelper.Callback callback) {
        ((StartActivity) context).getRuntimePermissionsHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback);
    }
}
