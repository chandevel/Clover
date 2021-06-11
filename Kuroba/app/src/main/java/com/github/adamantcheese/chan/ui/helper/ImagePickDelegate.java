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
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.net.NetUtils.MB;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImagePickDelegate {
    private static final int IMAGE_PICK_RESULT = 2;
    private static final long MAX_FILE_SIZE = 50 * MB;
    private static final String DEFAULT_FILE_NAME = "file";

    private final Activity activity;
    private ImagePickCallback callback;
    private Uri uri;
    private String fileName = "";
    private boolean success = false;

    @Nullable
    private Call cancelableDownload;

    public ImagePickDelegate(Activity activity) {
        this.activity = activity;
    }

    public void pick(ImagePickCallback callback, boolean longPressed) {
        BackgroundUtils.ensureMainThread();

        if (this.callback == null) {
            this.callback = callback;

            if (longPressed) {
                pickRemoteFile(callback);
            } else {
                pickLocalFile(callback);
            }
        }
    }

    private void pickLocalFile(ImagePickCallback callback) {
        PackageManager pm = getAppContext().getPackageManager();

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        List<Intent> intents = new ArrayList<>(resolveInfos.size());

        for (ResolveInfo info : resolveInfos) {
            Intent newIntent = new Intent(Intent.ACTION_GET_CONTENT);
            newIntent.addCategory(Intent.CATEGORY_OPENABLE);
            newIntent.setPackage(info.activityInfo.packageName);
            newIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            newIntent.setType("*/*");

            intents.add(newIntent);
        }

        if (!intents.isEmpty()) {
            if (intents.size() == 1 || !ChanSettings.allowFilePickChooser.get()) {
                activity.startActivityForResult(intents.get(0), IMAGE_PICK_RESULT);
            } else {
                Intent chooser = Intent.createChooser(intents.remove(intents.size() - 1),
                        getString(R.string.image_pick_delegate_select_file_picker)
                );

                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Intent[0]));
                activity.startActivityForResult(chooser, IMAGE_PICK_RESULT);
            }
        } else {
            showToast(activity, R.string.open_file_picker_failed, Toast.LENGTH_LONG);
            callback.onFilePickError(false);
            reset();
        }
    }

    private void pickRemoteFile(ImagePickCallback callback) {
        showToast(activity, R.string.image_url_get_attempt);
        HttpUrl clipboardURL;
        try {
            clipboardURL = HttpUrl.get(getClipboardContent().toString());
        } catch (Exception exception) {
            showToast(activity, getString(R.string.image_url_get_failed, exception.getMessage()));
            callback.onFilePickError(true);
            reset();

            return;
        }

        if (cancelableDownload != null) {
            cancelableDownload.cancel();
            cancelableDownload = null;
        }

        String urlFileName = clipboardURL.pathSegments().get(clipboardURL.pathSize() - 1);
        cancelableDownload = NetUtils.makeFileRequest(clipboardURL,
                "clipboard_url",
                Files.getFileExtension(urlFileName),
                new NetUtilsClasses.ResponseResult<File>() {
                    @Override
                    public void onFailure(Exception e) {
                        showToast(activity, getString(R.string.image_url_get_failed, e.getMessage()));
                        callback.onFilePickError(true);
                        reset();
                    }

                    @Override
                    public void onSuccess(File result) {
                        showToast(activity, R.string.image_url_get_success);
                        callback.onFilePicked(urlFileName, result);
                        reset();
                    }
                },
                null
        );
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (callback == null || requestCode != IMAGE_PICK_RESULT) {
            return;
        }

        boolean ok = false;
        boolean canceled = false;

        if (resultCode == Activity.RESULT_OK && data != null) {
            uri = getUriOrNull(data);
            if (uri != null) {
                Cursor returnCursor = activity.getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex > -1 && returnCursor.moveToFirst()) {
                        fileName = returnCursor.getString(nameIndex);
                    }

                    returnCursor.close();
                }

                if (TextUtils.isEmpty(fileName)) {
                    // As per the comment on OpenableColumns.DISPLAY_NAME:
                    // If this is not provided then the name should default to the last segment of the file's URI.
                    fileName = uri.getLastPathSegment();
                    fileName = TextUtils.isEmpty(fileName) ? DEFAULT_FILE_NAME : fileName;
                }

                BackgroundUtils.runOnBackgroundThread(this::doFilePicked);
                ok = true;
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            canceled = true;
        }

        if (!ok) {
            callback.onFilePickError(canceled);
            reset();
        }
    }

    @Nullable
    private Uri getUriOrNull(Intent intent) {
        if (intent.getData() != null) return intent.getData();

        ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }

        return null;
    }

    private void doFilePicked() {
        try (FileInputStream stream = new FileInputStream(activity.getContentResolver()
                .openFileDescriptor(uri, "r")
                .getFileDescriptor())) {
            if (stream.available() > MAX_FILE_SIZE) throw new IOException("File too large!");
            Files.asByteSink(getPickFile()).writeFrom(stream);
            success = true;
        } catch (Exception ignored) {
        }

        BackgroundUtils.runOnMainThread(() -> {
            if (success) {
                callback.onFilePicked(fileName, getPickFile());
            } else {
                callback.onFilePickError(false);
            }
            reset();
        });
    }

    public File getPickFile() {
        File cacheFile = new File(getCacheDir(), "picked_file");
        try {
            if (!cacheFile.exists()) cacheFile.createNewFile(); //ensure the file exists for writing to
        } catch (Exception ignored) {
        }
        return cacheFile;
    }

    private void reset() {
        callback = null;
        success = false;
        fileName = "";
        uri = null;
    }

    public interface ImagePickCallback {
        void onFilePicked(String fileName, File file);

        void onFilePickError(boolean canceled);
    }
}
