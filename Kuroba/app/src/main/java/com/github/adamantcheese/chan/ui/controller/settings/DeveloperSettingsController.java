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
import android.widget.Switch;
import android.widget.TextView;

import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.ui.controller.LogsController;
import com.github.adamantcheese.chan.utils.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class DeveloperSettingsController
        extends Controller {
    @Inject
    FilterWatchManager filterWatchManager;
    @Inject
    DatabaseHelper databaseHelper;
    @Inject
    WakeManager wakeManager;

    public DeveloperSettingsController(Context context) {
        super(context);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_developer);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        //VIEW LOGS
        Button logsButton = new Button(context);
        logsButton.setOnClickListener(v -> navigationController.pushController(new LogsController(context)));
        logsButton.setText(R.string.settings_open_logs);
        wrapper.addView(logsButton);

        // Enable/Disable verbose logs
        Switch verboseLogsSwitch = new Switch(context);
        verboseLogsSwitch.setText("Verbose logging");
        verboseLogsSwitch.setTextColor(getAttrColor(context, android.R.attr.textColor));
        verboseLogsSwitch.setChecked(ChanSettings.verboseLogs.get());
        verboseLogsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> ChanSettings.verboseLogs.toggle());
        wrapper.addView(verboseLogsSwitch);

        //CRASH APP
        Button crashButton = new Button(context);
        crashButton.setOnClickListener(v -> {
            throw new RuntimeException("Debug crash");
        });
        crashButton.setText("Crash the app");
        wrapper.addView(crashButton);

        //DATABASE SUMMARY
        TextView summaryText = new TextView(context);
        summaryText.setText("Database summary:\n" + DatabaseUtils.getDatabaseSummary());
        summaryText.setPadding(0, dp(5), 0, 0);
        wrapper.addView(summaryText);

        //APP RESET
        Button resetDbButton = new Button(context);
        resetDbButton.setOnClickListener(v -> {
            databaseHelper.reset();
            for (Field f : ChanSettings.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())
                        && Setting.class.isAssignableFrom(f.getType())) {
                    try {
                        Setting setting = (Setting) f.get(ChanSettings.class);
                        setting.setSync(setting.getDefault());
                    } catch (Exception e) {
                        Logger.e(this, "", e);
                    }
                }
            }
            for (Field f : PersistableChanState.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers())
                        && Setting.class.isAssignableFrom(f.getType())) {
                    try {
                        Setting setting = (Setting) f.get(ChanSettings.class);
                        setting.setSync(setting.getDefault());
                    } catch (Exception e) {
                        Logger.e(this, "", e);
                    }
                }
            }
            context.getSharedPreferences("com.skydoves.balloon", Context.MODE_PRIVATE).edit().clear().commit();
            ((PersistentCookieJar) NetUtils.applicationClient.cookieJar()).clear();
            ((StartActivity) context).restartApp();
        });
        resetDbButton.setText("Reset application and restart fresh");
        wrapper.addView(resetDbButton);

        //FILTER WATCH IGNORE RESET
        Button clearFilterWatchIgnores = new Button(context);
        clearFilterWatchIgnores.setOnClickListener(v -> {
            try {
                Field ignoredField = filterWatchManager.getClass().getDeclaredField("ignoredPosts");
                ignoredField.setAccessible(true);
                ignoredField.set(filterWatchManager, Collections.synchronizedSet(new HashSet<Integer>()));
                showToast(context, "Cleared ignores");
            } catch (Exception e) {
                showToast(context, "Failed to clear ignores");
            }
        });
        clearFilterWatchIgnores.setText("Clear ignored filter watches");
        wrapper.addView(clearFilterWatchIgnores);

        Button clearVideoTitleCache = new Button(context);
        clearVideoTitleCache.setOnClickListener(v -> {
            EmbeddingEngine.getInstance().clearCache();
            showToast(context, "Cleared video title cache");
        });
        clearVideoTitleCache.setText("Clear video title cache");
        wrapper.addView(clearVideoTitleCache);

        //THREAD STACK DUMPER
        Button dumpAllThreadStacks = new Button(context);
        dumpAllThreadStacks.setOnClickListener(v -> {
            Set<Thread> activeThreads = Thread.getAllStackTraces().keySet();
            Logger.i(this, "Thread count: " + activeThreads.size());
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
                Logger.i(this, "Thread: " + t.getName());
                for (StackTraceElement e : elements) {
                    Logger.i(this, e.toString());
                }
                Logger.i(this, "----------------");
            }
        });
        dumpAllThreadStacks.setText("Dump active thread stack traces to log");
        wrapper.addView(dumpAllThreadStacks);

        Switch threadCrashSwitch = new Switch(context);
        threadCrashSwitch.setText("Crash on wrong thread");
        threadCrashSwitch.setTextColor(getAttrColor(context, android.R.attr.textColor));
        threadCrashSwitch.setChecked(ChanSettings.crashOnWrongThread.get());
        threadCrashSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> ChanSettings.crashOnWrongThread.toggle());
        wrapper.addView(threadCrashSwitch);

        Switch noFunAllowed = new Switch(context);
        noFunAllowed.setText("Toggle fun");
        noFunAllowed.setTextColor(getAttrColor(context, android.R.attr.textColor));
        noFunAllowed.setChecked(PersistableChanState.noFunAllowed.get());
        noFunAllowed.setOnCheckedChangeListener((buttonView, isChecked) -> PersistableChanState.noFunAllowed.toggle());
        wrapper.addView(noFunAllowed);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(wrapper);
        view = scrollView;
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
    }
}
