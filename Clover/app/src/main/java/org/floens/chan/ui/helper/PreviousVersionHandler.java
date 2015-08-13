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
import android.support.v7.app.AlertDialog;
import android.text.Html;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;

public class PreviousVersionHandler {
    private static final int CURRENT_VERSION = 1;

    public void run(Context context) {
        if (ChanSettings.previousVersion.get() < CURRENT_VERSION) {
            CharSequence message = Html.fromHtml(context.getString(R.string.previous_version_1));

            new AlertDialog.Builder(context)
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();

            ChanSettings.previousVersion.set(CURRENT_VERSION);
        }
    }
}
