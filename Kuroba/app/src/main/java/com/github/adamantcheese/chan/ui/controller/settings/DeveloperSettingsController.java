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
package com.github.adamantcheese.chan.ui.controller.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.cache.FileCacheV2;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.LogsController;
import com.github.adamantcheese.chan.utils.Logger;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class DeveloperSettingsController
        extends Controller {
    private static final String TAG = "DEV";
    @Inject
    DatabaseManager databaseManager;
    @Inject
    FileCacheV2 fileCacheV2;
    @Inject
    CacheHandler cacheHandler;

    public DeveloperSettingsController(Context context) {
        super(context);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate() {
        super.onCreate();

        inject(this);

        navigation.setTitle(R.string.settings_developer);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        //VIEW LOGS
        Button logsButton = new Button(context);
        logsButton.setOnClickListener(v -> navigationController.pushController(new LogsController(context)));
        logsButton.setText(R.string.settings_open_logs);
        wrapper.addView(logsButton);

        // Enable/Disable verbose logs
        addVerboseLogsButton(wrapper);

        //CRASH APP
        Button crashButton = new Button(context);
        crashButton.setOnClickListener(v -> {
            throw new RuntimeException("Debug crash");
        });
        crashButton.setText("Crash the app");
        wrapper.addView(crashButton);

        //CLEAR CACHE
        Button clearCacheButton = new Button(context);

        clearCacheButton.setOnClickListener(v -> {
            fileCacheV2.clearCache();
            showToast("Cleared image cache");
            clearCacheButton.setText("Clear image cache (currently " + cacheHandler.getSize() / 1024 / 1024 + "MB)");
        });
        clearCacheButton.setText("Clear image cache (currently " + cacheHandler.getSize() / 1024 / 1024 + "MB)");
        wrapper.addView(clearCacheButton);

        //DATABASE SUMMARY
        TextView summaryText = new TextView(context);
        summaryText.setText("Database summary:\n" + databaseManager.getSummary());
        summaryText.setPadding(dp(15), dp(5), 0, 0);
        wrapper.addView(summaryText);

        //DATABASE RESET
        Button resetDbButton = new Button(context);
        resetDbButton.setOnClickListener(v -> {
            databaseManager.reset();
            ((StartActivity) context).restartApp();
        });
        resetDbButton.setText("Delete database & restart");
        wrapper.addView(resetDbButton);

        //FILTER WATCH IGNORE RESET
        Button clearFilterWatchIgnores = new Button(context);
        clearFilterWatchIgnores.setOnClickListener(v -> {
            try {
                FilterWatchManager filterWatchManager = instance(FilterWatchManager.class);
                Field ignoredField = filterWatchManager.getClass().getDeclaredField("ignoredPosts");
                ignoredField.setAccessible(true);
                ignoredField.set(filterWatchManager, Collections.synchronizedSet(new HashSet<Integer>()));
                showToast("Cleared ignores");
            } catch (Exception e) {
                showToast("Failed to clear ignores");
            }
        });
        clearFilterWatchIgnores.setText("Clear ignored filter watches");
        wrapper.addView(clearFilterWatchIgnores);

        //THREAD STACK DUMPER
        Button dumpAllThreadStacks = new Button(context);
        dumpAllThreadStacks.setOnClickListener(v -> {
            Set<Thread> activeThreads = Thread.getAllStackTraces().keySet();
            Logger.i("STACKDUMP-COUNT", String.valueOf(activeThreads.size()));
            for (Thread t : activeThreads) {
                //ignore these threads as they aren't relevant (main will always be this button press)
                //@formatter:off
                if (t.getName().equalsIgnoreCase("main")
                        || t.getName().contains("Daemon")
                        || t.getName().equalsIgnoreCase("Signal Catcher")
                        || t.getName().contains("hwuiTask")
                        || t.getName().contains("Binder:")
                        || t.getName().equalsIgnoreCase("RenderThread")
                        || t.getName().contains("maginfier pixel")
                        || t.getName().contains("Jit thread")
                        || t.getName().equalsIgnoreCase("Profile Saver")
                        || t.getName().contains("Okio")
                        || t.getName().contains("AsyncTask"))
                //@formatter:on
                    continue;
                StackTraceElement[] elements = t.getStackTrace();
                Logger.i("STACKDUMP-HEADER", "Thread: " + t.getName());
                for (StackTraceElement e : elements) {
                    Logger.i("STACKDUMP", e.toString());
                }
                Logger.i("STACKDUMP-FOOTER", "----------------");
            }
        });
        dumpAllThreadStacks.setText("Dump active thread stack traces to log");
        wrapper.addView(dumpAllThreadStacks);

        //FORCE WAKE
        Button forceWake = new Button(context);
        forceWake.setOnClickListener(v -> {
            try {
                WakeManager wakeManager = instance(WakeManager.class);
                Field wakeables = wakeManager.getClass().getDeclaredField("wakeableSet");
                wakeables.setAccessible(true);
                for (WakeManager.Wakeable wakeable : (Set<WakeManager.Wakeable>) wakeables.get(wakeManager)) {
                    wakeable.onWake();
                }
                showToast("Woke all wakeables");
            } catch (Exception e) {
                showToast("Failed to run wakeables");
            }
        });
        forceWake.setText("Force wakemanager wake");
        wrapper.addView(forceWake);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(wrapper);
        view = scrollView;
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
    }

    private void addVerboseLogsButton(LinearLayout wrapper) {
        Button verboseLogsButton = new Button(context);

        verboseLogsButton.setOnClickListener(v -> {
            ChanSettings.verboseLogs.set(!ChanSettings.verboseLogs.get());

            if (ChanSettings.verboseLogs.get()) {
                showToast("Verbose logs enabled");
                verboseLogsButton.setText(R.string.settings_disable_verbose_logs);
            } else {
                showToast("Verbose logs disabled");
                verboseLogsButton.setText(R.string.settings_enable_verbose_logs);
            }
        });

        if (ChanSettings.verboseLogs.get()) {
            verboseLogsButton.setText(R.string.settings_disable_verbose_logs);
        } else {
            verboseLogsButton.setText(R.string.settings_enable_verbose_logs);
        }

        wrapper.addView(verboseLogsButton);
    }
}
