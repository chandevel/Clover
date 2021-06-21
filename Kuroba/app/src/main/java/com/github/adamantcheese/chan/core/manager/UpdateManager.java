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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.net.UpdateApiParser;
import com.github.adamantcheese.chan.core.net.UpdateApiParser.UpdateApiResponse;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.BuildConfig.COMMIT_HASH;
import static com.github.adamantcheese.chan.BuildConfig.DEV_API_ENDPOINT;
import static com.github.adamantcheese.chan.BuildConfig.DEV_BUILD;
import static com.github.adamantcheese.chan.BuildConfig.UPDATE_API_ENDPOINT;
import static com.github.adamantcheese.chan.BuildConfig.UPDATE_DELAY;
import static com.github.adamantcheese.chan.BuildConfig.VERSION_CODE;
import static com.github.adamantcheese.chan.BuildConfig.VERSION_NAME;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static java.util.concurrent.TimeUnit.DAYS;

/**
 * Calls the update API and downloads and requests installs of APK files.
 * <p>The APK files are downloaded to the public Download directory, and the default APK install
 * screen is launched after downloading.
 */
public class UpdateManager {
    private ProgressDialog updateDownloadDialog;
    private final Context context;

    @Nullable
    private Call updateDownload;

    public UpdateManager(Context context) {
        this.context = context;
    }

    /**
     * Runs every time onCreate is called on the StartActivity.
     */
    public void autoUpdateCheck() {
        if (!DEV_BUILD && PersistableChanState.previousVersion.get() < VERSION_CODE) {
            // Show dialog because release updates are infrequent so it's fine
            Spanned text = Html.fromHtml("<h3>" + BuildConfig.APP_LABEL + " was updated to " + VERSION_NAME + ".</h3>");
            final AlertDialog dialog =
                    getDefaultAlertBuilder(context).setMessage(text).setPositiveButton(R.string.ok, null).create();
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();

            PersistableChanState.previousVersion.setSync(VERSION_CODE);
            cancelApkUpdateNotification();

            return;
        }

        if (DEV_BUILD && !PersistableChanState.previousDevHash.get().equals(COMMIT_HASH)) {
            // Show toast because dev updates may happen every day (to avoid alert dialog spam)
            showToast(context, BuildConfig.APP_LABEL + " was updated to the latest commit.");

            PersistableChanState.previousDevHash.setSync(COMMIT_HASH);
            cancelApkUpdateNotification();

            return;
        }

        runUpdateApi(false);
    }

    public void manualUpdateCheck() {
        runUpdateApi(true);
    }

    private void runUpdateApi(final boolean manual) {
        if (!manual) {
            long lastUpdateTime = PersistableChanState.updateCheckTime.get();
            long interval = DAYS.toMillis(UPDATE_DELAY);
            long now = System.currentTimeMillis();
            long delta = (lastUpdateTime + interval) - now;
            if (delta > 0) {
                if (PersistableChanState.hasNewApkUpdate.get()) {
                    notifyNewApkUpdate();
                }
                return;
            } else {
                PersistableChanState.updateCheckTime.setSync(now);
            }
        }

        Logger.d(this, "Calling update API");
        if (!DEV_BUILD) {
            NetUtils.makeJsonRequest(HttpUrl.get(UPDATE_API_ENDPOINT),
                    new NetUtilsClasses.MainThreadResponseResult<>(new ResponseResult<UpdateApiResponse>() {
                        @Override
                        public void onFailure(Exception e) {
                            failedUpdate(manual);
                        }

                        @Override
                        public void onSuccess(UpdateApiResponse result) {
                            if (!processUpdateApiResponse(result, manual) && manual
                                    && BackgroundUtils.isInForeground()) {
                                showToast(context,
                                        getString(R.string.update_none, BuildConfig.APP_LABEL),
                                        Toast.LENGTH_LONG
                                );
                            }
                        }
                    }),
                    new UpdateApiParser(),
                    NetUtilsClasses.NO_CACHE
            );
        } else {
            NetUtils.makeJsonRequest(HttpUrl.get(DEV_API_ENDPOINT),
                    new NetUtilsClasses.MainThreadResponseResult<>(new ResponseResult<UpdateApiResponse>() {
                        @Override
                        public void onFailure(Exception e) {
                            failedUpdate(manual);
                        }

                        @Override
                        public void onSuccess(UpdateApiResponse result) {
                            if (result.commitHash.equals(COMMIT_HASH)) {
                                //same version and commit, no update needed
                                if (manual && BackgroundUtils.isInForeground()) {
                                    showToast(context,
                                            getString(R.string.update_none, BuildConfig.APP_LABEL),
                                            Toast.LENGTH_LONG
                                    );
                                }

                                cancelApkUpdateNotification();
                            } else {
                                processUpdateApiResponse(result, manual);
                            }
                        }
                    }),
                    new UpdateApiParser(),
                    NetUtilsClasses.NO_CACHE
            );
        }
    }

    private boolean processUpdateApiResponse(UpdateApiResponse response, boolean manual) {
        if ((response.versionCode > VERSION_CODE || DEV_BUILD) && BackgroundUtils.isInForeground()) {

            // Do not spam dialogs if this is not the manual update check, use the notifications instead
            if (manual) {
                boolean concat = !response.updateTitle.isEmpty();
                CharSequence updateMessage =
                        concat ? TextUtils.concat(response.updateTitle, "; ", response.body) : response.body;
                AlertDialog dialog = getDefaultAlertBuilder(context).setTitle(
                        BuildConfig.APP_LABEL + " " + response.versionCodeString + " available")
                        .setMessage(updateMessage)
                        .setNegativeButton(R.string.update_later, null)
                        .setPositiveButton(R.string.update_install, (dialog1, which) -> {
                            updateDownloadDialog = new ProgressDialog(context);
                            updateDownloadDialog.setCanceledOnTouchOutside(true);
                            updateDownloadDialog.setOnDismissListener((d) -> {
                                showToast(context, "Download will continue in background.");
                                updateDownloadDialog = null;
                            });
                            updateDownloadDialog.setTitle(R.string.update_install_downloading);
                            updateDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                            updateDownloadDialog.setProgressNumberFormat("");
                            updateDownloadDialog.show();
                            doUpdate(response);
                        })
                        .create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
            notifyNewApkUpdate();
            return true;
        }

        cancelApkUpdateNotification();
        return false;
    }

    private void notifyNewApkUpdate() {
        PersistableChanState.hasNewApkUpdate.setSync(true);
        SettingsNotificationManager.postNotification(SettingNotification.ApkUpdate);
    }

    private void cancelApkUpdateNotification() {
        PersistableChanState.hasNewApkUpdate.setSync(false);
        SettingsNotificationManager.cancelNotification(SettingNotification.ApkUpdate);
    }

    private void failedUpdate(boolean manual) {
        Logger.e(this, "Failed to process " + (DEV_BUILD ? "dev" : "stable") + " API call for updating");
        if (manual && BackgroundUtils.isInForeground()) {
            getDefaultAlertBuilder(context).setTitle(R.string.update_check_failed)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    /**
     * Install the APK file specified in {@code update}. This methods needs the storage permission.
     *
     * @param response that contains the APK file URL
     */
    public void doUpdate(UpdateApiResponse response) {
        BackgroundUtils.ensureMainThread();

        if (updateDownload != null) {
            updateDownload.cancel();
            updateDownload = null;
        }

        updateDownload = NetUtils.makeFileRequest(response.apkURL,
                BuildConfig.APP_LABEL + "_" + response.versionCodeString,
                "apk",
                new NetUtilsClasses.MainThreadResponseResult<>(new ResponseResult<File>() {
                    @Override
                    public void onFailure(Exception e) {
                        if (!BackgroundUtils.isInForeground()) return;

                        if (updateDownloadDialog != null) {
                            updateDownloadDialog.setOnDismissListener(null);
                            updateDownloadDialog.dismiss();
                            updateDownloadDialog = null;
                        }
                        getDefaultAlertBuilder(context).setTitle(R.string.update_install_download_failed)
                                .setMessage(getString(R.string.update_install_download_failed_description,
                                        e.getMessage()
                                ))
                                .setPositiveButton(R.string.ok, null)
                                .show();
                    }

                    @Override
                    public void onSuccess(File result) {
                        if (updateDownloadDialog != null) {
                            updateDownloadDialog.setOnDismissListener(null);
                            updateDownloadDialog.dismiss();
                            updateDownloadDialog = null;
                        }

                        if (!BackgroundUtils.isInForeground()) return;

                        Uri apkURI = FileProvider.getUriForFile(context, BuildConfig.FILE_PROVIDER, result);
                        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(apkURI, "application/vnd.android.package-archive");
                        openIntent(intent);
                    }
                }),
                (url, bytesRead, contentLength, start, done) -> {
                    if (updateDownloadDialog != null) {
                        updateDownloadDialog.setProgress((int) (updateDownloadDialog.getMax() * (bytesRead / (double) contentLength)));
                    }
                }
        );
    }
}
