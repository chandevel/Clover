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

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.widget.Button;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.io.File;

public class PreviousVersionHandler {
    private static final String TAG = "PreviousVersionHandler";
    private static final int CURRENT_VERSION = 1;

    public void run(Context context) {
        int previous = ChanSettings.previousVersion.get();
        if (previous < CURRENT_VERSION) {
            if (previous < 1) {
                cleanupOutdatedIonFolder(context);
            }

            showMessage(context, CURRENT_VERSION);

            ChanSettings.previousVersion.set(CURRENT_VERSION);
        }
    }

    private void showMessage(Context context, int version) {
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
