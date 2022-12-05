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

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.TaskResult.Failure;
import static com.github.adamantcheese.chan.core.saver.ImageSaver.TaskResult.Success;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;

import android.content.*;
import android.media.MediaScannerConnection;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.github.adamantcheese.chan.*;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.SavedFilesBaseDirectory;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.DirectorySegment;
import com.github.k1rakishou.fsaf.util.FSAFUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.subjects.SingleSubject;
import okhttp3.Call;

public class ImageSaveTask {
    @Inject
    FileManager fileManager;

    public final PostImage postImage;
    private AbstractFile finalSaveLocation;
    public final boolean share;
    private String subFolder;
    private boolean success = false;
    private final SingleSubject<ImageSaver.TaskResult> imageSaveTaskAsyncResult;

    public ImageSaveTask(PostImage postImage, boolean share) {
        inject(this);

        this.postImage = postImage;
        this.share = share;
        this.imageSaveTaskAsyncResult = SingleSubject.create();
    }

    /**
     * Set the subfolder(s) that this task should save to.
     *
     * @param subFolder A string representing the subfolder to save to, with included file separators.
     */
    public void setSubFolderLocation(String subFolder) {
        this.subFolder = subFolder;
    }

    /**
     * Set the actual destination of the save task.
     *
     * @param destination A file object representing the location that this task will save to.
     */
    public void setFinalSaveLocation(AbstractFile destination) {
        this.finalSaveLocation = destination;
    }

    public String getSavedName() {
        return fileManager.getName(finalSaveLocation);
    }

    /**
     * Sets up the folders specified by setSubFolderLocation.
     *
     * @return A file object representing the target folder.
     */
    @Nullable
    public AbstractFile getSaveLocation() {
        AbstractFile baseSaveDir = fileManager.newBaseDirectoryFile(SavedFilesBaseDirectory.class);
        if (baseSaveDir == null) {
            Logger.e(this, "getSaveLocation() fileManager.newSaveLocationFile() returned null");
            return null;
        }

        AbstractFile createdBaseSaveDir = fileManager.create(baseSaveDir);

        if (!fileManager.exists(baseSaveDir) || createdBaseSaveDir == null) {
            Logger.e(this, "getSaveLocation() Couldn't create base image save directory");
            return null;
        }

        if (!fileManager.baseDirectoryExists(SavedFilesBaseDirectory.class)) {
            Logger.e(this, "getSaveLocation() Base save local directory does not exist");
            return null;
        }

        if (subFolder != null) {
            List<String> segments = FSAFUtils.splitIntoSegments(subFolder);
            if (segments.isEmpty()) {
                return baseSaveDir;
            }

            List<DirectorySegment> directorySegments = new ArrayList<>(segments.size());

            // All segments should be directory segments since we are creating sub-directories so it
            // should be safe to get rid of cloneUnsafe() and use regular clone()
            for (String dirSegment : segments) {
                directorySegments.add(new DirectorySegment(dirSegment));
            }

            AbstractFile innerDirectory = fileManager.create(baseSaveDir, directorySegments);

            if (innerDirectory == null) {
                Logger.e(this,
                        "getSaveLocation() failed to create subdirectory ("
                                + subFolder
                                + ") for a base dir: "
                                + baseSaveDir.getFullPath()
                );
            }

            return innerDirectory;
        }

        return baseSaveDir;
    }

    public Single<ImageSaver.TaskResult> run() {
        BackgroundUtils.ensureBackgroundThread();
        Logger.d(this, "ImageSaveTask.run() destination = " + finalSaveLocation.getFullPath());

        @Nullable
        Action onDisposeFunc = null;

        try {
            if (fileManager.exists(finalSaveLocation)) {
                BackgroundUtils.runOnMainThread(() -> {
                    onDestination();
                    onEnd();
                });
            } else {
                Call download = NetUtils.makeFileRequest(postImage.imageUrl,
                        postImage.filename,
                        postImage.extension,
                        new NetUtilsClasses.ResponseResult<File>() {
                            @Override
                            public void onFailure(Exception e) {
                                BackgroundUtils.ensureMainThread();
                                imageSaveTaskAsyncResult.onError(e);

                                onEnd();
                            }

                            @Override
                            public void onSuccess(File response) {
                                BackgroundUtils.ensureMainThread();

                                if (copyToDestination(response)) {
                                    onDestination();
                                } else {
                                    if (fileManager.exists(finalSaveLocation)) {
                                        if (!fileManager.delete(finalSaveLocation)) {
                                            Logger.w(this, "Could not delete destination file after error");
                                        }
                                    }
                                }

                                if (!share && !response.delete()) {
                                    Logger.w(this, "Could not delete cached file");
                                }
                                onEnd();
                            }
                        },
                        null
                );

                onDisposeFunc = () -> {
                    if (download != null) {
                        download.cancel();
                    }
                };
            }
        } catch (Exception e) {
            imageSaveTaskAsyncResult.onError(e);
        }

        if (onDisposeFunc != null) {
            return imageSaveTaskAsyncResult.doOnDispose(onDisposeFunc);
        }

        return imageSaveTaskAsyncResult;
    }

    private void onEnd() {
        BackgroundUtils.ensureMainThread();
        imageSaveTaskAsyncResult.onSuccess(success ? Success : Failure);
    }

    private void onDestination() {
        success = true;
        if (share) {
            Uri file = FileProvider.getUriForFile(getAppContext(),
                    BuildConfig.FILE_PROVIDER,
                    new File(finalSaveLocation.getFullPath())
            );

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getAppContext().getContentResolver().getType(file));
            intent.putExtra(Intent.EXTRA_STREAM, file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            openIntent(intent);
            return;
        }

        try {
            String[] paths = {finalSaveLocation.getFullPath()};
            MediaScannerConnection.scanFile(getAppContext(), paths, null, null);
        } catch (Exception ignored) {}
    }

    private boolean copyToDestination(File source) {
        try {
            if (share) {
                finalSaveLocation = fileManager.fromRawFile(source);
            } else {
                AbstractFile createdDestinationFile = fileManager.create(finalSaveLocation);
                if (createdDestinationFile == null) {
                    throw new IOException("Could not create destination file, path = "
                            + finalSaveLocation.getFullPath());
                }

                if (fileManager.isDirectory(createdDestinationFile)) {
                    throw new IOException("Destination file is already a directory");
                }

                if (!fileManager.copyFileContents(fileManager.fromRawFile(source), createdDestinationFile)) {
                    throw new IOException("Could not copy source file into destination");
                }
            }
            return true;
        } catch (Throwable e) {
            boolean exists = fileManager.exists(finalSaveLocation);
            boolean canWrite = fileManager.canWrite(finalSaveLocation);

            Logger.e(this,
                    "Error writing to file: ("
                            + finalSaveLocation.getFullPath()
                            + "), "
                            + "exists = "
                            + exists
                            + ", canWrite = "
                            + canWrite,
                    e
            );
            //@formatter:off
            EventBus.getDefault()
                    .post(new StartActivity.ActivityAlertDialogMessage(
                            "Couldn't save your file; you're probably on a newer Android version and the older file "
                            + "API no longer allows for direct access to the save location. You can fix this by"
                            + " changing your save location in media settings and use the SAF API instead."));
            //@formatter:on
        }

        return false;
    }

    public static boolean copyImageToClipboard(Context context, @Nullable PostImage image) {
        if (image == null) return false;
        if (ChanSettings.copyImage.get()) {
            NetUtils.makeFileRequest(image.imageUrl,
                    image.filename,
                    image.extension,
                    new NetUtilsClasses.ResponseResult<File>() {
                        @Override
                        public void onFailure(Exception e) {
                            setClipboardContent("Image URL", image.imageUrl.toString());
                            showToast(context, R.string.image_copied_failed);
                        }

                        @Override
                        public void onSuccess(File result) {
                            Uri imageUri =
                                    FileProvider.getUriForFile(getAppContext(), BuildConfig.FILE_PROVIDER, result);
                            getClipboardManager().setPrimaryClip(ClipData.newUri(
                                    context.getContentResolver(),
                                    "Post image",
                                    imageUri
                            ));
                            showToast(context, R.string.image_copied_to_clipboard);
                        }
                    },
                    null
            );
        } else {
            setClipboardContent("Image URL", image.imageUrl.toString());
            showToast(context, R.string.image_url_copied_to_clipboard);
        }
        return true;
    }
}
