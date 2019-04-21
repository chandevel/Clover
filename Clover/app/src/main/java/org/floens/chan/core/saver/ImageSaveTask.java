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

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.cache.FileCacheDownloader;
import org.floens.chan.core.cache.FileCacheListener;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class ImageSaveTask extends FileCacheListener implements Runnable {
    private static final String TAG = "ImageSaveTask";

    @Inject
    FileCache fileCache;

    private PostImage postImage;
    private ImageSaveTaskCallback callback;
    private File destination;
    private boolean share;
    private String subFolder;

    private boolean success = false;

    public ImageSaveTask(PostImage postImage) {
        inject(this);
        this.postImage = postImage;
    }

    public void setSubFolder(String boardName) {
        this.subFolder = boardName;
    }

    public String getSubFolder() {
        return subFolder;
    }

    public void setCallback(ImageSaveTaskCallback callback) {
        this.callback = callback;
    }

    public PostImage getPostImage() {
        return postImage;
    }

    public void setDestination(File destination) {
        this.destination = destination;
    }

    public File getDestination() {
        return destination;
    }

    public void setShare(boolean share) {
        this.share = share;
    }

    @Override
    public void run() {
        try {
            if (destination.exists()) {
                onDestination();
                // Manually call postFinished()
                postFinished(success);
            } else {
                FileCacheDownloader fileCacheDownloader =
                        fileCache.downloadFile(postImage.imageUrl.toString(), this);

                // If the fileCacheDownloader is null then the destination already existed and onSuccess() has been called.
                // Wait otherwise for the download to finish to avoid that the next task is immediately executed.
                if (fileCacheDownloader != null) {
                    // If the file is now downloading
                    fileCacheDownloader.getFuture().get();
                }
            }
        } catch (InterruptedException e) {
            onInterrupted();
        } catch (Exception e) {
            Logger.e(TAG, "Uncaught exception", e);
        }
    }

    @Override
    public void onSuccess(File file) {
        if (copyToDestination(file)) {
            onDestination();
        } else {
            deleteDestination();
        }
    }

    @Override
    public void onEnd() {
        postFinished(success);
    }

    private void onInterrupted() {
        deleteDestination();
    }

    private void deleteDestination() {
        if (destination.exists()) {
            if (!destination.delete()) {
                Logger.e(TAG, "Could not delete destination after an interrupt");
            }
        }
    }

    private void onDestination() {
        success = true;
        MediaScannerConnection.scanFile(getAppContext(), new String[]{destination.getAbsolutePath()}, null, (path, uri) -> {
            // Runs on a binder thread
            AndroidUtils.runOnUiThread(() -> afterScan(uri));
        });
    }

    private boolean copyToDestination(File source) {
        boolean result = false;

        try {
            File parent = destination.getParentFile();
            if (!parent.mkdirs() && !parent.isDirectory()) {
                throw new IOException("Could not create parent directory");
            }

            if (destination.isDirectory()) {
                throw new IOException("Destination file is already a directory");
            }

            IOUtils.copyFile(source, destination);

            result = true;
        } catch (IOException e) {
            Logger.e(TAG, "Error writing to file", e);
        }

        return result;
    }

    private void afterScan(final Uri uri) {
        Logger.d(TAG, "Media scan succeeded: " + uri);

        if (share) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            AndroidUtils.openIntent(intent);
        }
    }

    private void postFinished(final boolean success) {
        AndroidUtils.runOnUiThread(() ->
                callback.imageSaveTaskFinished(ImageSaveTask.this, success));
    }

    public interface ImageSaveTaskCallback {
        void imageSaveTaskFinished(ImageSaveTask task, boolean success);
    }
}
