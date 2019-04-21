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
package org.floens.chan.core.update;


import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.text.TextUtils;

import com.android.volley.RequestQueue;

import org.floens.chan.BuildConfig;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.cache.FileCacheListener;
import org.floens.chan.core.net.UpdateApiRequest;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static org.floens.chan.Chan.inject;

/**
 * Calls the update API and downloads and requests installs of APK files.
 * <p>The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */
public class UpdateManager {
    public static final long DEFAULT_UPDATE_CHECK_INTERVAL_MS = 1000 * 60 * 60 * 24 * 5; // 5 days

    private static final String TAG = "UpdateManager";

    private static final String DOWNLOAD_FILE = "Clover_update.apk";

    @Inject
    RequestQueue volleyRequestQueue;

    @Inject
    FileCache fileCache;

    private UpdateCallback callback;

    public UpdateManager(UpdateCallback callback) {
        inject(this);
        this.callback = callback;
    }

    public boolean isUpdatingAvailable() {
        return !TextUtils.isEmpty(BuildConfig.UPDATE_API_ENDPOINT);
    }

    public void runUpdateApi(final boolean manual) {
        if (!manual) {
            long lastUpdateTime = ChanSettings.updateCheckTime.get();
            long interval = ChanSettings.updateCheckInterval.get();
            long now = Time.get();
            long delta = (lastUpdateTime + interval) - now;
            if (delta > 0) {
                return;
            } else {
                ChanSettings.updateCheckTime.set(now);
            }
        }

        Logger.d(TAG, "Calling update API");
        volleyRequestQueue.add(new UpdateApiRequest(response -> {
            if (!processUpdateApiResponse(response) && manual) {
                callback.onManualCheckNone();
            }
        }, error -> {
            Logger.e(TAG, "Failed to process API call for updating", error);

            if (manual) {
                callback.onManualCheckFailed();
            }
        }));
    }

    private boolean processUpdateApiResponse(UpdateApiRequest.UpdateApiResponse response) {
        if (response.newerApiVersion) {
            Logger.e(TAG, "API endpoint reports a higher API version than we support, " +
                    "aborting update check.");

            // ignore
            return false;
        }

        if (response.checkIntervalMs != 0) {
            ChanSettings.updateCheckInterval.set(response.checkIntervalMs);
        }

        for (UpdateApiRequest.UpdateApiMessage message : response.messages) {
            if (processUpdateMessage(message)) {
                return true;
            }
        }

        return false;
    }

    private boolean processUpdateMessage(UpdateApiRequest.UpdateApiMessage message) {
        if (isMessageRelevantForThisVersion(message)) {
            if (message.type.equals(UpdateApiRequest.TYPE_UPDATE)) {
                if (message.apkUrl == null) {
                    Logger.i(TAG, "Update available but none for this build flavor.");
                    // Not for this flavor, discard.
                    return false;
                }

                Logger.i(TAG, "Update available (" + message.code + ") with url \"" +
                        message.apkUrl + "\".");
                callback.showUpdateAvailableDialog(message);
                return true;
            }
        }

        return false;
    }

    private boolean isMessageRelevantForThisVersion(UpdateApiRequest.UpdateApiMessage message) {
        if (message.code != 0) {
            if (message.code <= BuildConfig.VERSION_CODE) {
                Logger.d(TAG, "No newer version available (" +
                        BuildConfig.VERSION_CODE + " >= " + message.code + ").");
                // Our code is newer than the message
                return false;
            } else {
                return true;
            }
        } else if (message.hash != null) {
            return !message.hash.equals(BuildConfig.BUILD_HASH);
        } else {
            Logger.w(TAG, "No code or hash found");
            return false;
        }
    }

    /**
     * Install the APK file specified in {@code update}. This methods needs the storage permission.
     *
     * @param update update with apk details.
     */
    public void doUpdate(Update update) {
        fileCache.downloadFile(update.apkUrl.toString(), new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                callback.onUpdateDownloadProgress(downloaded, total);
            }

            @Override
            public void onSuccess(File file) {
                callback.onUpdateDownloadSuccess();
                copyToPublicDirectory(file);
            }

            @Override
            public void onFail(boolean notFound) {
                callback.onUpdateDownloadFailed();
            }

            @Override
            public void onCancel() {
                callback.onUpdateDownloadFailed();
            }
        });
    }

    public void retry(Install install) {
        installApk(install);
    }

    private void copyToPublicDirectory(File cacheFile) {
        File out = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_FILE);
        try {
            IOUtils.copyFile(cacheFile, out);
        } catch (IOException e) {
            Logger.e(TAG, "requestApkInstall", e);
            callback.onUpdateDownloadMoveFailed();
            return;
        }
        installApk(new Install(out));
    }

    private void installApk(Install install) {
        // First open the dialog that asks to retry and calls this method again.
        callback.openUpdateRetryDialog(install);

        // Then launch the APK install intent.
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setDataAndType(Uri.fromFile(install.installFile),
                "application/vnd.android.package-archive");

        // The installer wants a content scheme from android N and up,
        // but I don't feel like implementing a content provider just for this feature.
        // Temporary change the strictmode policy while starting the intent.
        StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);

        callback.onUpdateOpenInstallScreen(intent);

        StrictMode.setVmPolicy(vmPolicy);
    }

    public static class Update {
        private HttpUrl apkUrl;

        public Update(HttpUrl apkUrl) {
            this.apkUrl = apkUrl;
        }
    }

    public static class Install {
        private File installFile;

        public Install(File installFile) {
            this.installFile = installFile;
        }
    }

    public interface UpdateCallback {
        void onManualCheckNone();

        void onManualCheckFailed();

        void showUpdateAvailableDialog(UpdateApiRequest.UpdateApiMessage message);

        void onUpdateDownloadProgress(long downloaded, long total);

        void onUpdateDownloadSuccess();

        void onUpdateDownloadFailed();

        void onUpdateDownloadMoveFailed();

        void onUpdateOpenInstallScreen(Intent intent);

        void openUpdateRetryDialog(Install install);
    }
}
