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
package com.github.adamantcheese.chan.core.manager;


import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.widget.Button;

import com.android.volley.RequestQueue;
import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.cache.FileCacheListener;
import com.github.adamantcheese.chan.core.net.UpdateApiRequest;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

/**
 * Calls the update API and downloads and requests installs of APK files.
 * <p>The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */
public class UpdateManager {
    private static final String TAG = "UpdateManager";

    @Inject
    RequestQueue volleyRequestQueue;

    @Inject
    FileCache fileCache;

    private ProgressDialog updateDownloadDialog;
    private Context context;

    public UpdateManager(Context context) {
        inject(this);
        this.context = context;
    }

    /**
     * Runs every time onCreate is called on the StartActivity.
     */
    public void autoUpdateCheck() {
        if (ChanSettings.previousVersion.get() < BuildConfig.VERSION_CODE
                && ChanSettings.previousVersion.get() != 0) {
            Spanned text = Html.fromHtml("<h3>Kuroba was updated</h3>Kuroba was updated to " + BuildConfig.VERSION_NAME);
            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setMessage(text)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();

            final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setEnabled(false);
            AndroidUtils.runOnUiThread(() -> {
                dialog.setCanceledOnTouchOutside(true);
                button.setEnabled(true);
            }, 1500);

            // Also set the new app version to not show this message again
            ChanSettings.previousVersion.set(BuildConfig.VERSION_CODE);

            // Don't process the updater because a dialog is now already showing.
            return;
        }

        runUpdateApi(false);
    }

    public void manualUpdateCheck() {
        runUpdateApi(true);
    }

    private void runUpdateApi(final boolean manual) {
        if (!manual) {
            long lastUpdateTime = ChanSettings.updateCheckTime.get();
            long interval = 1000 * 60 * 60 * 24 * 5; //5 days
            long now = System.currentTimeMillis();
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
                new AlertDialog.Builder(context)
                        .setTitle(R.string.update_none)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }, error -> {
            Logger.e(TAG, "Failed to process API call for updating", error);

            if (manual) {
                new AlertDialog.Builder(context)
                        .setTitle(R.string.update_check_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        }));
    }

    private boolean processUpdateApiResponse(UpdateApiRequest.UpdateApiResponse response) {
        Spanned text = Html.fromHtml("<h2>Kuroba update ready</h2>A new Kuroba version is available.<br><br>Changelog:<br>" + response.body);
        if (response.versionCode > BuildConfig.VERSION_CODE) {
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setMessage(text)
                    .setNegativeButton(R.string.update_later, null)
                    .setPositiveButton(R.string.update_install, (dialog1, which) -> updateInstallRequested(response))
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return true;
        }
        return false;
    }

    /**
     * Install the APK file specified in {@code update}. This methods needs the storage permission.
     *
     * @param response that contains the APK file URL
     */
    public void doUpdate(UpdateApiRequest.UpdateApiResponse response) {
        fileCache.downloadFile(response.apkURL.toString(), new FileCacheListener() {
            @Override
            public void onProgress(long downloaded, long total) {
                updateDownloadDialog.setProgress((int) (updateDownloadDialog.getMax() * (downloaded / (double) total)));
            }

            @Override
            public void onSuccess(File file) {
                updateDownloadDialog.dismiss();
                updateDownloadDialog = null;
                File copy = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS),
                        "Kuroba.apk");
                try {
                    IOUtils.copyFile(file, copy);
                } catch (IOException e) {
                    Logger.e(TAG, "requestApkInstall", e);
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.update_install_download_move_failed)
                            .setPositiveButton(R.string.ok, null)
                            .show();
                    return;
                }
                installApk(copy);
            }

            @Override
            public void onFail(boolean notFound) {
                updateDownloadDialog.dismiss();
                updateDownloadDialog = null;
                new AlertDialog.Builder(context)
                        .setTitle(R.string.update_install_download_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }

            @Override
            public void onCancel() {
                updateDownloadDialog.dismiss();
                updateDownloadDialog = null;
                new AlertDialog.Builder(context)
                        .setTitle(R.string.update_install_download_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show();
            }
        });
    }

    private void installApk(File apk) {
        // First open the dialog that asks to retry and calls this method again.
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_retry_title)
                .setMessage(R.string.update_retry)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.update_retry_button, (dialog, which) -> installApk(apk))
                .show();

        // Then launch the APK install intent.
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(Uri.fromFile(apk),
                "application/vnd.android.package-archive");

        // The installer wants a content scheme from android N and up,
        // but I don't feel like implementing a content provider just for this feature.
        // Temporary change the strictmode policy while starting the intent.
        StrictMode.VmPolicy vmPolicy = StrictMode.getVmPolicy();
        StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);

        AndroidUtils.openIntent(intent);

        StrictMode.setVmPolicy(vmPolicy);
    }

    private void updateInstallRequested(final UpdateApiRequest.UpdateApiResponse response) {
        RuntimePermissionsHelper runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (granted) {
                updateDownloadDialog = new ProgressDialog(context);
                updateDownloadDialog.setCancelable(false);
                updateDownloadDialog.setTitle(R.string.update_install_downloading);
                updateDownloadDialog.setMax(10000);
                updateDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                updateDownloadDialog.setProgressNumberFormat("");
                updateDownloadDialog.show();
                doUpdate(response);
            } else {
                runtimePermissionsHelper.showPermissionRequiredDialog(context,
                        context.getString(R.string.update_storage_permission_required_title),
                        context.getString(R.string.update_storage_permission_required),
                        () -> updateInstallRequested(response));
            }
        });
    }
}
