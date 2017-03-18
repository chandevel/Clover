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
package org.floens.chan.ui.helper;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.widget.Button;

import org.floens.chan.R;
import org.floens.chan.core.net.UpdateApiRequest;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.update.UpdateManager;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.io.File;

public class VersionHandler implements UpdateManager.UpdateCallback {
    private static final String TAG = "VersionHandler";

    /*
     * Manifest version code, manifest version name, this version mapping:
     *
     * 28 = v1.1.2
     * 32 = v1.1.3
     * 36 = v1.2.0
     * 39 = v1.2.1
     * 40 = v1.2.2
     * 41 = v1.2.3
     * 42 = v1.2.4
     * 43 = v1.2.5
     * 44 = v1.2.6
     * 46 = v1.2.7
     * 47 = v1.2.8
     * 48 = v1.2.9
     * 49 = v1.2.10
     * 50 = v1.2.11
     * 51 = v2.0.0 = 1
     * 52 = v2.1.0 = 2
     * 53 = v2.1.1 = 2
     * 54 = v2.1.2 = 2
     * 55 = v2.1.3 = 2
     * 56 = v2.2.0 = 3
     */
    private static final int CURRENT_VERSION = 3;

    /**
     * Context to show dialogs to.
     */
    private Context context;
    private RuntimePermissionsHelper runtimePermissionsHelper;

    private UpdateManager updateManager;

    private ProgressDialog updateDownloadDialog;

    public VersionHandler(Context context, RuntimePermissionsHelper runtimePermissionsHelper) {
        this.context = context;
        this.runtimePermissionsHelper = runtimePermissionsHelper;

        updateManager = new UpdateManager(this);
    }

    /**
     * Runs every time onCreate is called on the StartActivity.
     */
    public void run() {
        int previous = ChanSettings.previousVersion.get();
        if (previous < CURRENT_VERSION) {
            if (previous < 1) {
                cleanupOutdatedIonFolder(context);
            }

            // Add more previous version checks here

            showMessage(CURRENT_VERSION);

            ChanSettings.previousVersion.set(CURRENT_VERSION);

            // Don't process the updater because a dialog is now already showing.
            return;
        }

        if (updateManager.isUpdatingAvailable()) {
            updateManager.runUpdateApi(false);
        }
    }

    public boolean isUpdatingAvailable() {
        return updateManager.isUpdatingAvailable();
    }

    public void manualUpdateCheck() {
        updateManager.runUpdateApi(true);
    }

    @Override
    public void onManualCheckNone() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_none)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onManualCheckFailed() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_check_failed)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void showUpdateAvailableDialog(final UpdateApiRequest.UpdateApiMessage message) {
        Spanned text = Html.fromHtml(message.messageHtml);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(text)
                .setNegativeButton(R.string.update_later, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updatePostponed(message);
                    }
                })
                .setPositiveButton(R.string.update_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updateInstallRequested(message);
                    }
                })
                .create();
        dialog.show();
        dialog.setCanceledOnTouchOutside(false);
    }

    private void updatePostponed(UpdateApiRequest.UpdateApiMessage message) {
    }

    private void updateInstallRequested(final UpdateApiRequest.UpdateApiMessage message) {
        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, new RuntimePermissionsHelper.Callback() {
            @Override
            public void onRuntimePermissionResult(boolean granted) {
                if (granted) {
                    createDownloadProgressDialog();
                    updateManager.doUpdate(new UpdateManager.Update(message.apkUrl));
                } else {
                    runtimePermissionsHelper.showPermissionRequiredDialog(context,
                            context.getString(R.string.update_storage_permission_required_title),
                            context.getString(R.string.update_storage_permission_required),
                            new RuntimePermissionsHelper.PermissionRequiredDialogCallback() {
                                @Override
                                public void retryPermissionRequest() {
                                    updateInstallRequested(message);
                                }
                            });
                }
            }
        });
    }

    private void createDownloadProgressDialog() {
        updateDownloadDialog = new ProgressDialog(context);
        updateDownloadDialog.setCancelable(false);
        updateDownloadDialog.setTitle(R.string.update_install_downloading);
        updateDownloadDialog.setMax(10000);
        updateDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        updateDownloadDialog.setProgressNumberFormat("");
        updateDownloadDialog.show();
    }

    @Override
    public void onUpdateDownloadProgress(long downloaded, long total) {
        updateDownloadDialog.setProgress((int) (updateDownloadDialog.getMax() * (downloaded / (double) total)));
    }

    @Override
    public void onUpdateDownloadSuccess() {
        updateDownloadDialog.dismiss();
        updateDownloadDialog = null;
    }

    @Override
    public void onUpdateDownloadFailed() {
        updateDownloadDialog.dismiss();
        updateDownloadDialog = null;
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_install_download_failed)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onUpdateDownloadMoveFailed() {
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_install_download_move_failed)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onUpdateOpenInstallScreen(Intent intent) {
        AndroidUtils.openIntent(intent);
    }

    @Override
    public void openUpdateRetryDialog(final UpdateManager.Install install) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.update_retry_title)
                .setMessage(R.string.update_retry)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.update_retry_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        updateManager.retry(install);
                    }
                })
                .show();
    }

    private void showMessage(int version) {
        int resource = context.getResources().getIdentifier("previous_version_" + version, "string", context.getPackageName());
        if (resource != 0) {
            CharSequence message = Html.fromHtml(context.getString(resource));

            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            dialog.show();
            dialog.setCanceledOnTouchOutside(false);

            final Button button = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            button.setEnabled(false);
            AndroidUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dialog.setCanceledOnTouchOutside(true);
                    button.setEnabled(true);
                }
            }, 1500);
        }
    }

    private void cleanupOutdatedIonFolder(Context context) {
        Logger.i(TAG, "Cleaning up old ion folder");
        File ionCacheFolder = new File(context.getCacheDir() + "/ion");
        if (ionCacheFolder.exists() && ionCacheFolder.isDirectory()) {
            Logger.i(TAG, "Clearing old ion folder");
            for (File file : ionCacheFolder.listFiles()) {
                if (!file.delete()) {
                    Logger.i(TAG, "Could not delete old ion file " + file.getName());
                }
            }
            if (!ionCacheFolder.delete()) {
                Logger.i(TAG, "Could not delete old ion folder");
            } else {
                Logger.i(TAG, "Deleted old ion folder");
            }
        }
    }
}
