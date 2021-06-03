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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.IOException;
import java.io.InputStreamReader;

import kotlin.io.TextStreamsKt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;

public class LogsController
        extends Controller {
    private static final String TAG = "LogsController";
    private static final int DEFAULT_LINES_COUNT = 250;

    private TextView logTextView;

    public LogsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_logs_screen);
        navigation.buildMenu().withItem(R.drawable.ic_fluent_clipboard_code_24_filled, this::copyLogsClicked).build();

        ScrollView container = new ScrollView(context);
        container.setPadding(dp(8), dp(8), dp(8), dp(8));
        container.setBackgroundColor(Color.BLACK);
        logTextView = new TextView(context);
        logTextView.setTypeface(Typeface.MONOSPACE);
        logTextView.setTextColor(Color.WHITE);
        logTextView.setLineSpacing(dp(1), 1);
        container.addView(logTextView, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        view = container;

        String logs = loadLogs();
        if (logs != null) {
            logTextView.setText(logs);
        }
    }

    private void copyLogsClicked(ToolbarMenuItem item) {
        setClipboardContent("Logs", logTextView.getText().toString());
        showToast(context, R.string.settings_logs_copied_to_clipboard);
    }

    @Nullable
    public static String loadLogs() {
        return loadLogs(DEFAULT_LINES_COUNT);
    }

    @Nullable
    public static String loadLogs(int linesCount) {
        Process process;
        try {
            process = new ProcessBuilder().command("logcat",
                    "-v",
                    "tag",
                    "-t",
                    String.valueOf(linesCount),
                    "StrictMode:S"
            ).start();
        } catch (IOException e) {
            Logger.e(TAG, "Error starting logcat", e);
            return null;
        }

        //This filters our log output to just stuff we care about in-app (and if a crash happens, the uncaught handler gets it and this will still allow it through)
        String filtered = "";
        for (String line : TextStreamsKt.readLines(new InputStreamReader(process.getInputStream()))) {
            if (line.contains(BuildConfig.APP_LABEL)) filtered = filtered.concat(line).concat("\n");
        }

        return filtered;
    }
}
