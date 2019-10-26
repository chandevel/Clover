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
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.service.SavingNotification;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImageSaver implements ImageSaveTask.ImageSaveTaskCallback {
    private static final String TAG = "ImageSaver";
    private static final int MAX_NAME_LENGTH = 50;
    private static final Pattern UNSAFE_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9._\\\\ -]");
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private int doneTasks = 0;
    private int totalTasks = 0;
    private Toast toast;

    public ImageSaver() {
        EventBus.getDefault().register(this);
    }

    public void startDownloadTask(Context context, final ImageSaveTask task) {
        PostImage postImage = task.getPostImage();
        task.setDestination(deduplicateFile(postImage, task));

        if (hasPermission(context)) {
            startTask(task);
            updateNotification();
        } else {
            // This does not request the permission when another request is pending.
            // This is ok and will drop the task.
            requestPermission(context, granted -> {
                if (granted) {
                    startTask(task);
                    updateNotification();
                } else {
                    showToast(null, false, false);
                }
            });
        }
    }

    public boolean startBundledTask(Context context, final String subFolder, final List<ImageSaveTask> tasks) {
        if (hasPermission(context)) {
            startBundledTaskInternal(subFolder, tasks);
            return true;
        } else {
            // This does not request the permission when another request is pending.
            // This is ok and will drop the tasks.
            requestPermission(context, granted -> {
                if (granted) {
                    startBundledTaskInternal(subFolder, tasks);
                } else {
                    showToast(null, false, false);
                }
            });
            return false;
        }
    }

    public String getSubFolder(String name) {
        String filtered = filterName(name);
        filtered = filtered.substring(0, Math.min(filtered.length(), MAX_NAME_LENGTH));
        return filtered;
    }

    public File getSaveLocation(ImageSaveTask task) {
        String base = ChanSettings.saveLocation.get();
        String subFolder = task.getSubFolder();
        if (subFolder != null) {
            return new File(base + File.separator + subFolder);
        } else {
            return new File(base);
        }
    }

    @Override
    public void imageSaveTaskFinished(ImageSaveTask task, boolean success) {
        doneTasks++;
        boolean wasAlbumSave = false;
        if (doneTasks == totalTasks) {
            wasAlbumSave = totalTasks > 1;
            totalTasks = 0;
            doneTasks = 0;
        }
        updateNotification();
        showToast(task, success, wasAlbumSave);
    }

    @Subscribe
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
            task.setSubFolder(subFolder);
            task.setDestination(deduplicateFile(postImage, task));

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

    private void showToast(ImageSaveTask task, boolean success, boolean wasAlbumSave) {
        if (task == null && success)
            throw new IllegalArgumentException("Task succeeded but is null");

        if (toast != null) {
            toast.cancel();
        }

        String text = success ?
                (wasAlbumSave ? getAppContext().getString(R.string.album_download_success, getSaveLocation(task).getPath()) : getAppContext().getString(R.string.image_save_as, task.getDestination().getName())) :
                getString(R.string.image_save_failed);
        toast = Toast.makeText(getAppContext(), text, Toast.LENGTH_LONG);
        if (task != null && !task.getShare()) {
            toast.show();
        }
    }

    private String filterName(String name) {
        name = UNSAFE_CHARACTERS_PATTERN.matcher(name).replaceAll("");
        if (name.length() == 0) {
            name = "_";
        }
        return name;
    }

    private File deduplicateFile(PostImage postImage, ImageSaveTask task) {
        String name = ChanSettings.saveServerFilename.get() ? postImage.serverFilename : postImage.filename;
        String fileName = filterName(name + "." + postImage.extension);
        File saveFile = new File(getSaveLocation(task), fileName);
        while (saveFile.exists()) {
            fileName = filterName(name + "_" + Long.toString(SystemClock.elapsedRealtimeNanos(), Character.MAX_RADIX) + "." + postImage.extension);
            saveFile = new File(getSaveLocation(task), fileName);
        }
        return saveFile;
    }

    private boolean hasPermission(Context context) {
        return ((StartActivity) context).getRuntimePermissionsHelper().hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission(Context context, RuntimePermissionsHelper.Callback callback) {
        ((StartActivity) context).getRuntimePermissionsHelper().requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback);
    }
}
