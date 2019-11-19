package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class ExperimentalSettingsController extends SettingsController {
    private static final String TAG = "ExperimentalSettingsController";

    public ExperimentalSettingsController(Context context) {
        super(context);
    }

    private BooleanSettingView incrementalThreadDownloadingSetting;

    @Inject
    ThreadSaveManager threadSaveManager;
    @Inject
    DatabaseManager databaseManager;

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

        if (item == incrementalThreadDownloadingSetting && !ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            cancelAllDownloads();
        }
    }

    private void cancelAllDownloads() {
        threadSaveManager.cancelAllDownloading();

        databaseManager.runTask(() -> {
            List<Pin> pins = databaseManager.getDatabasePinManager().getPins().call();
            if (pins.isEmpty()) {
                return null;
            }

            List<Pin> downloadPins = new ArrayList<>(10);

            for (Pin pin : pins) {
                if (PinType.hasDownloadFlag(pin.pinType)) {
                    downloadPins.add(pin);
                }
            }

            if (downloadPins.isEmpty()) {
                return null;
            }

            databaseManager.getDatabaseSavedThreadManager().deleteAllSavedThreads().call();

            for (Pin pin : downloadPins) {
                pin.pinType = PinType.removeDownloadNewPostsFlag(pin.pinType);

                if (PinType.hasWatchNewPostsFlag(pin.pinType)) {
                    continue;
                }

                // We don't want to delete all of the users's bookmarks so we just change their
                // types to WatchNewPosts
                pin.pinType = PinType.addWatchNewPostsFlag(pin.pinType);
            }

            databaseManager.getDatabasePinManager().updatePins(downloadPins).call();

            for (Pin pin : downloadPins) {
                databaseManager.getDatabaseSavedThreadManager().deleteThreadFromDisk(
                        pin.loadable
                );
            }

            return null;
        });
    }

    private void populatePreferences() {
        SettingsGroup group = new SettingsGroup(context.getString(R.string.experimental_settings_group));

        incrementalThreadDownloadingSetting = new BooleanSettingView(this,
                ChanSettings.incrementalThreadDownloadingEnabled,
                context.getString(R.string.incremental_thread_downloading_title),
                context.getString(R.string.incremental_thread_downloading_description));
        requiresRestart.add(group.add(incrementalThreadDownloadingSetting));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parseYoutubeTitles,
                R.string.setting_youtube_title, R.string.setting_youtube_title_description)));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parseYoutubeDuration,
                R.string.setting_youtube_dur_title, R.string.setting_youtube_dur_description)));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.parsePostImageLinks,
                context.getString(R.string.setting_image_link_loading_title),
                context.getString(R.string.setting_image_link_loading_description))));

        requiresUiRefresh.add(group.add(new BooleanSettingView(this,
                ChanSettings.addDubs,
                R.string.add_dubs_title,
                R.string.add_dubs_description)));

        groups.add(group);
    }
}
