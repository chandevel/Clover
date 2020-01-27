package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ExperimentalSettingsController
        extends SettingsController {

    public ExperimentalSettingsController(Context context) {
        super(context);
    }

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
    }

    private void populatePreferences() {
        SettingsGroup group = new SettingsGroup(getString(R.string.experimental_settings_group));

        group.add(new BooleanSettingView(
                this,
                ChanSettings.videoStream,
                R.string.setting_video_stream,
                R.string.setting_video_stream_description
        ));

        setupConcurrentFileDownloadingChunksSetting(group);

        groups.add(group);
    }

    private void setupConcurrentFileDownloadingChunksSetting(SettingsGroup group) {
        List<ListSettingView.Item> items = new ArrayList<>();

        for (ChanSettings.ConcurrentFileDownloadingChunks setting : ChanSettings.ConcurrentFileDownloadingChunks.values()) {
            items.add(new ListSettingView.Item<>(setting.getKey(), setting));
        }

        requiresRestart.add(group.add(new ListSettingView<ChanSettings.ConcurrentFileDownloadingChunks>(
                this,
                ChanSettings.concurrentDownloadChunkCount,
                getString(R.string.settings_concurrent_file_downloading_name),
                items
        ) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.settings_concurrent_file_downloading_description) + "\n\n" + items.get(
                        selected).name;
            }
        }));
    }
}
