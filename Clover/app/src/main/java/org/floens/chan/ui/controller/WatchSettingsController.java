package org.floens.chan.ui.controller;

import android.content.Context;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.preferences.BooleanSettingView;
import org.floens.chan.ui.preferences.SettingsController;
import org.floens.chan.ui.preferences.SettingsGroup;

public class WatchSettingsController extends SettingsController {
    public WatchSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_screen_advanced);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(string(R.string.settings_group_watch));

        settings.add(new BooleanSettingView(this, ChanSettings.watchCountdown, string(R.string.setting_watch_countdown), string(R.string.setting_watch_countdown_description)));
        settings.add(new BooleanSettingView(this, ChanSettings.watchBackground, string(R.string.setting_watch_enable_background), string(R.string.setting_watch_enable_background_description)));

        groups.add(settings);
    }
}
