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

public class PreviousVersionHandler {
    private static final int CURRENT_VERSION = 1;

    public void run(Context context) {
        if (ChanSettings.previousVersion.get() < CURRENT_VERSION) {
            int resource = context.getResources().getIdentifier("previous_version_" + CURRENT_VERSION, "string", context.getPackageName());
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

            ChanSettings.previousVersion.set(CURRENT_VERSION);
        }
    }
}
