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
package com.github.adamantcheese.chan.ui.controller;

import android.content.ClipData;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.utils.IOUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.io.InputStream;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class LogsController
        extends Controller {
    private static final String TAG = "LogsController";

    private TextView logTextView;

    private String logText;

    public LogsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_logs_screen);

        navigation.buildMenu()
                .withOverflow()
                .withSubItem(R.string.settings_logs_copy, this::copyLogsClicked)
                .build()
                .build();

        ScrollView container = new ScrollView(context);
        container.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
        logTextView = new TextView(context);
        container.addView(logTextView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        view = container;

        String logs = loadLogs();
        if (logs != null) {
            logText = logs;
            logTextView.setText(logText);
        }
    }

    private void copyLogsClicked(ToolbarMenuSubItem item) {
        ClipData clip = ClipData.newPlainText("Logs", logText);
        getClipboardManager().setPrimaryClip(clip);
        showToast(R.string.settings_logs_copied_to_clipboard);
    }

    @Nullable
    public static String loadLogs() {
        Process process;
        try {
            process = new ProcessBuilder().command("logcat", "-v", "tag", "-t", "250", "StrictMode:S").start();
        } catch (IOException e) {
            Logger.e(TAG, "Error starting logcat", e);
            return null;
        }

        InputStream outputStream = process.getInputStream();
        //This filters our log output to just stuff we care about in-app (and if a crash happens, the uncaught handler gets it and this will still allow it through)
        String filtered = "";
        for (String line : IOUtils.readString(outputStream).split("\n")) {
            if (line.contains(getApplicationLabel())) filtered = filtered.concat(line).concat("\n");
        }

        return filtered;
    }
}
