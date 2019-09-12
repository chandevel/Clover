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

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.cache.FileCache;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.utils.Logger;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class DeveloperSettingsController extends Controller {
    private static final String TAG = "DEV";
    @Inject
    DatabaseManager databaseManager;

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

        Button logsButton = new Button(context);
        logsButton.setOnClickListener(v -> navigationController.pushController(new LogsController(context)));
        logsButton.setText(R.string.settings_open_logs);
        wrapper.addView(logsButton);

        Button crashButton = new Button(context);
        crashButton.setOnClickListener(v -> {
            throw new RuntimeException("Debug crash");
        });
        crashButton.setText("Crash the app");
        wrapper.addView(crashButton);

        Button clearCacheButton = new Button(context);
        FileCache cache = Chan.injector().instance(FileCache.class);
        clearCacheButton.setOnClickListener(v -> {
            cache.clearCache();
            Toast.makeText(context, "Cleared image cache", Toast.LENGTH_SHORT).show();
            clearCacheButton.setText("Clear image cache (currently " + cache.getFileCacheSize() / 1024 / 1024 + "MB)");
        });
        clearCacheButton.setText("Clear image cache (currently " + cache.getFileCacheSize() / 1024 / 1024 + "MB)");
        wrapper.addView(clearCacheButton);

        TextView summaryText = new TextView(context);
        summaryText.setText("Database summary:\n" + databaseManager.getSummary());
        summaryText.setPadding(dp(15), dp(5), 0, 0);
        wrapper.addView(summaryText);

        Button resetDbButton = new Button(context);
        resetDbButton.setOnClickListener(v -> {
            databaseManager.reset();
            ((StartActivity) context).restartApp();
        });
        resetDbButton.setText("Delete database");
        wrapper.addView(resetDbButton);

        Button clearFilterWatchIgnores = new Button(context);
        clearFilterWatchIgnores.setOnClickListener(v -> {
            try {
                FilterWatchManager filterWatchManager = Chan.injector().instance(FilterWatchManager.class);
                Field ignoredField = filterWatchManager.getClass().getDeclaredField("ignoredPosts");
                ignoredField.setAccessible(true);
                ignoredField.set(filterWatchManager, Collections.synchronizedSet(new HashSet<Integer>()));
                ignoredField.setAccessible(false);
                Logger.d(TAG, "Cleared ignores");
            } catch (Exception e) {
                Logger.d(TAG, "Failed to clear ignores");
            }
        });
        clearFilterWatchIgnores.setText("Clear ignored filter watches");
        wrapper.addView(clearFilterWatchIgnores);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(wrapper);
        view = scrollView;
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
    }
}
