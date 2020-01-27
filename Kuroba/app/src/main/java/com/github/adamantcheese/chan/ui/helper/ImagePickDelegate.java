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
package com.github.adamantcheese.chan.ui.helper;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.cache.downloader.CancelableDownload;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.file.RawFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

public class ImagePickDelegate
        implements Runnable {
    private static final String TAG = "ImagePickActivity";

    private static final int IMAGE_PICK_RESULT = 2;
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;
    private static final String DEFAULT_FILE_NAME = "file";

    @Inject
    ReplyManager replyManager;
    @Inject
    FileManager fileManager;
    @Inject
    FileCacheV2 fileCacheV2;

    private Activity activity;

    private ImagePickCallback callback;
    private Uri uri;
    private String fileName;
    private boolean success = false;
    private RawFile cacheFile;

    @Nullable
    private CancelableDownload cancelableDownload;

    public ImagePickDelegate(Activity activity) {
        this.activity = activity;
        inject(this);
    }

    public void pick(ImagePickCallback callback, boolean longPressed) {
        BackgroundUtils.ensureMainThread();

        if (this.callback == null) {
            this.callback = callback;

            if (longPressed) {
                showToast(R.string.image_url_get_attempt);
                HttpUrl clipboardURL = null;
                try {
                    clipboardURL =
                            HttpUrl.get(getClipboardManager().getPrimaryClip().getItemAt(0).getText().toString());
                } catch (Exception exception) {
                    showToast(getString(R.string.image_url_get_failed, exception.getMessage()));
                    callback.onFilePickError(true);
                    reset();
                }
                if (clipboardURL != null) {
                    HttpUrl finalClipboardURL = clipboardURL;

                    if (cancelableDownload != null) {
                        cancelableDownload.cancel();
                        cancelableDownload = null;
                    }

                    cancelableDownload = fileCacheV2.enqueueNormalDownloadFileRequest(clipboardURL.toString(),
                            new FileCacheListener() {
                                @Override
                                public void onSuccess(RawFile file) {
                                    BackgroundUtils.ensureMainThread();

                                    showToast(R.string.image_url_get_success);
                                    Uri imageURL = Uri.parse(finalClipboardURL.toString());

                                    callback.onFilePicked(imageURL.getLastPathSegment(), new File(file.getFullPath()));

                                    reset();
                                }

                                @Override
                                public void onNotFound() {
                                    onFail(new IOException("Not found"));
                                }

                                @Override
                                public void onFail(Exception exception) {
                                    BackgroundUtils.ensureMainThread();

                                    String message = getString(R.string.image_url_get_failed, exception.getMessage());

                                    showToast(message);
                                    callback.onFilePickError(true);
                                    reset();
                                }
                            }
                    );
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");

                if (intent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivityForResult(intent, IMAGE_PICK_RESULT);
                } else {
                    Logger.e(TAG, "No activity found to get file with");
                    callback.onFilePickError(false);
                    reset();
                }
            }
        }
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callback == null) {
            return false;
        }

        if (requestCode != IMAGE_PICK_RESULT) {
            return false;
        }

        boolean ok = false;
        boolean canceled = false;

        if (resultCode == Activity.RESULT_OK && data != null) {
            uri = data.getData();

            Cursor returnCursor = activity.getContentResolver().query(uri, null, null, null, null);
            if (returnCursor != null) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                returnCursor.moveToFirst();
                if (nameIndex > -1) {
                    fileName = returnCursor.getString(nameIndex);
                }

                returnCursor.close();
            }

            if (fileName == null) {
                // As per the comment on OpenableColumns.DISPLAY_NAME:
                // If this is not provided then the name should default to the last segment of the file's URI.
                fileName = uri.getLastPathSegment();
            }

            if (fileName == null) {
                fileName = DEFAULT_FILE_NAME;
            }

            new Thread(this).start();
            ok = true;
        } else if (resultCode == Activity.RESULT_CANCELED) {
            canceled = true;
        }

        if (!ok) {
            callback.onFilePickError(canceled);
            reset();
        }

        return true;
    }

    @Override
    public void run() {
        cacheFile = fileManager.fromRawFile(replyManager.getPickFile());

        InputStream is = null;
        OutputStream os = null;
        try (ParcelFileDescriptor fileDescriptor = activity.getContentResolver().openFileDescriptor(uri, "r")) {
            if (fileDescriptor == null) {
                throw new IOException("Couldn't open file descriptor for uri = " + uri);
            }

            is = new FileInputStream(fileDescriptor.getFileDescriptor());
            os = fileManager.getOutputStream(cacheFile);

            if (os == null) {
                throw new IOException(
                        "Could not get OutputStream from the cacheFile, cacheFile = " + cacheFile.getFullPath());
            }

            success = IOUtils.copy(is, os, MAX_FILE_SIZE);
        } catch (IOException | SecurityException e) {
            Logger.e(TAG, "Error copying file from the file descriptor", e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }

        if (!success) {
            if (!fileManager.delete(cacheFile)) {
                Logger.e(TAG, "Could not delete picked_file after copy fail");
            }
        }

        runOnUiThread(() -> {
            if (success) {
                callback.onFilePicked(fileName, new File(cacheFile.getFullPath()));
            } else {
                callback.onFilePickError(false);
            }
            reset();
        });
    }

    private void reset() {
        callback = null;
        cacheFile = null;
        success = false;
        fileName = null;
        uri = null;
    }

    public void onDestroy() {
        if (cancelableDownload != null) {
            cancelableDownload.cancel();
            cancelableDownload = null;
        }
    }

    public interface ImagePickCallback {
        void onFilePicked(String fileName, File file);

        void onFilePickError(boolean canceled);
    }
}
