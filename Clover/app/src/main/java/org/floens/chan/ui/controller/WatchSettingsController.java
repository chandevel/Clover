package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.preferences.BooleanSettingView;
import org.floens.chan.ui.preferences.ListSettingView;
import org.floens.chan.ui.preferences.SettingView;
import org.floens.chan.ui.preferences.SettingsController;
import org.floens.chan.ui.preferences.SettingsGroup;
import org.floens.chan.utils.AndroidUtils;

public class WatchSettingsController extends SettingsController {
    public WatchSettingsController(Context context) {
        super(context);
    }

    private SettingView enableBackground;
    private SettingView backgroundTimeout;
    private SettingView notifyMode;
    private SettingView soundMode;
    private SettingView ledMode;

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_screen_advanced);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();

        setEnabledHeights();
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableBackground) {
            boolean enabled = ChanSettings.watchBackground.get();
            AndroidUtils.animateHeight(backgroundTimeout.view, enabled);
            AndroidUtils.animateHeight(notifyMode.view, enabled);
            AndroidUtils.animateHeight(soundMode.view, enabled);
            AndroidUtils.animateHeight(ledMode.view, enabled);
        }
    }

    private void setEnabledHeights() {
        if (!ChanSettings.watchBackground.get()) {
            backgroundTimeout.view.setVisibility(View.GONE);
            notifyMode.view.setVisibility(View.GONE);
            soundMode.view.setVisibility(View.GONE);
            ledMode.view.setVisibility(View.GONE);
        }
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(string(R.string.settings_group_watch));

        settings.add(new BooleanSettingView(this, ChanSettings.watchCountdown, string(R.string.setting_watch_countdown), string(R.string.setting_watch_countdown_description)));
        enableBackground = settings.add(new BooleanSettingView(this, ChanSettings.watchBackground, string(R.string.setting_watch_enable_background), string(R.string.setting_watch_enable_background_description)));

        int[] timeouts = new int[]{1, 2, 3, 5, 10, 30, 60};
        ListSettingView.Item[] timeoutsItems = new ListSettingView.Item[timeouts.length];
        for (int i = 0; i < timeouts.length; i++) {
            String name = context.getResources().getQuantityString(R.plurals.minutes, timeouts[i], timeouts[i]);
            timeoutsItems[i] = new ListSettingView.Item(name, String.valueOf(timeouts[i]));
        }
        backgroundTimeout = settings.add(new ListSettingView(this, ChanSettings.watchBackgroundTimeout, string(R.string.setting_watch_background_timeout), timeoutsItems));

        notifyMode = settings.add(new ListSettingView(this, ChanSettings.watchNotifyMode, string(R.string.setting_watch_notify_mode),
                context.getResources().getStringArray(R.array.setting_watch_notify_modes), new String[]{"all", "quotes"}));

        soundMode = settings.add(new ListSettingView(this, ChanSettings.watchSound, string(R.string.setting_watch_sound),
                context.getResources().getStringArray(R.array.setting_watch_sounds), new String[]{"all", "quotes"}));

        ledMode = settings.add(new ListSettingView(this, ChanSettings.watchLed, string(R.string.setting_watch_led),
                context.getResources().getStringArray(R.array.setting_watch_leds),
                new String[]{"-1", "ffffffff", "ffff0000", "ffffff00", "ff00ff00", "ff00ffff", "ff0000ff", "ffff00ff"}));

        groups.add(settings);
    }
}
