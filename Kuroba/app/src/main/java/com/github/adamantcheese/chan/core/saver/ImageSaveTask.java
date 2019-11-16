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

import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.ExternalFile;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import kotlin.NotImplementedError;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class ImageSaveTask extends FileCacheListener implements Runnable {
    private static final String TAG = "ImageSaveTask";

    @Inject
    FileCache fileCache;
    @Inject
    FileManager fileManager;

    private PostImage postImage;
    private Loadable loadable;
    private ImageSaveTaskCallback callback;
    private AbstractFile destination;
    private boolean share;
    private String subFolder;

    private boolean success = false;

    public ImageSaveTask(Loadable loadable, PostImage postImage) {
        inject(this);
        this.loadable = loadable;
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

    public void setDestination(AbstractFile destination) {
        this.destination = destination;
    }

    public AbstractFile getDestination() {
        return destination;
    }

    public void setShare(boolean share) {
        this.share = share;
    }

    public boolean getShare() {
        return share;
    }

    @Override
    public void run() {
        try {
            if (fileManager.exists(destination)) {
                onDestination();
                // Manually call postFinished()
                postFinished(success);
            } else {
                fileCache.downloadFile(loadable, postImage, this);
            }
        } catch (Exception e) {
            Logger.e(TAG, "Uncaught exception", e);
        }
    }

    @Override
    public void onSuccess(RawFile file) {
        if (copyToDestination(new File(file.getFullPath()))) {
            onDestination();
        } else {
            deleteDestination();
        }
    }

    @Override
    public void onEnd() {
        postFinished(success);
    }

    private void deleteDestination() {
        if (fileManager.exists(destination)) {
            if (!fileManager.delete(destination)) {
                Logger.e(TAG, "Could not delete destination after an interrupt");
            }
        }
    }

    private void onDestination() {
        success = true;

        if (destination instanceof RawFile) {
            String[] paths = {destination.getFullPath()};

            MediaScannerConnection.scanFile(getAppContext(), paths, null, (path, uri) -> {
                // Runs on a binder thread
                AndroidUtils.runOnUiThread(() -> afterScan(uri));
            });
        } else if (destination instanceof ExternalFile) {
            Uri uri = Uri.parse(destination.getFullPath());

            AndroidUtils.runOnUiThread(() -> afterScan(uri));
        } else {
            throw new NotImplementedError("Not implemented for " + destination.getClass().getName());
        }
    }

    private boolean copyToDestination(File source) {
        boolean result = false;

        try {
            AbstractFile createdDestinationFile = fileManager.create(destination);
            if (createdDestinationFile == null) {
                throw new IOException("Could not create destination file, path = " + destination.getFullPath());
            }

            if (fileManager.isDirectory(createdDestinationFile)) {
                throw new IOException("Destination file is already a directory");
            }

            if (!fileManager.copyFileContents(fileManager.fromRawFile(source), createdDestinationFile)) {
                throw new IOException("Could not copy source file into destination");
            }

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
