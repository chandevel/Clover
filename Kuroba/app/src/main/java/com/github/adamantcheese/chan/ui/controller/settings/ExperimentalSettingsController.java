package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ExperimentalSettingsController
        extends SettingsController {
    private static final String TAG = "ExperimentalSettingsController";

    public ExperimentalSettingsController(Context context) {
        super(context);
    }

    private BooleanSettingView incrementalThreadDownloadingSetting;

    @Inject
    ThreadSaveManager threadSaveManager;
    @Inject
    WatchManager watchManager;

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_experimental_settings_title);

        setupLayout();
        populatePreferences();
        buildPreferences();

        inject(this);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == incrementalThreadDownloadingSetting &&
                !ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            watchManager.stopSavingAllThread();
            threadSaveManager.cancelAllDownloading();
        }
    }

    private void populatePreferences() {
        SettingsGroup group = new SettingsGroup(getString(R.string.experimental_settings_group));

        incrementalThreadDownloadingSetting = new BooleanSettingView(this,
                ChanSettings.incrementalThreadDownloadingEnabled,
                R.string.incremental_thread_downloading_title,
                R.string.incremental_thread_downloading_description
        );
        requiresRestart.add(group.add(incrementalThreadDownloadingSetting));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parseYoutubeTitles,
                R.string.setting_youtube_title,
                R.string.setting_youtube_title_description
        )));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parseYoutubeDuration,
                R.string.setting_youtube_dur_title,
                R.string.setting_youtube_dur_description
        )));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parsePostImageLinks,
                R.string.setting_image_link_loading_title,
                R.string.setting_image_link_loading_description
        )));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.addDubs,
                R.string.add_dubs_title,
                R.string.add_dubs_description
        )));

        groups.add(group);
    }
}
