package org.floens.chan.ui.controller;

import android.content.Context;
import android.support.v7.widget.SwitchCompat;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.preferences.BooleanSettingView;
import org.floens.chan.ui.preferences.ListSettingView;
import org.floens.chan.ui.preferences.SettingView;
import org.floens.chan.ui.preferences.SettingsController;
import org.floens.chan.ui.preferences.SettingsGroup;
import org.floens.chan.ui.view.CrossfadeView;

public class WatchSettingsController extends SettingsController implements CompoundButton.OnCheckedChangeListener {
    private CrossfadeView crossfadeView;

    private SettingView enableBackground;

    private SettingView backgroundTimeout;
    private SettingView notifyMode;
    private SettingView soundMode;
    private SettingView ledMode;

    public WatchSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        boolean enabled = ChanSettings.watchEnabled.get();

        navigationItem.title = string(R.string.settings_screen_watch);

        view = inflateRes(R.layout.settings_watch);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);
        crossfadeView = (CrossfadeView) view.findViewById(R.id.crossfade);

        crossfadeView.toggle(enabled, false);

        SwitchCompat globalSwitch = new SwitchCompat(context);
        globalSwitch.setChecked(enabled);
        globalSwitch.setOnCheckedChangeListener(this);
        navigationItem.rightView = globalSwitch;

        populatePreferences();

        buildPreferences();

        if (!ChanSettings.watchBackground.get()) {
            setSettingViewVisibility(backgroundTimeout, false, false);
            setSettingViewVisibility(notifyMode, false, false);
            setSettingViewVisibility(soundMode, false, false);
            setSettingViewVisibility(ledMode, false, false);
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        ChanSettings.watchEnabled.set(isChecked);
        ((WatchSettingControllerListener) navigationController.getPreviousSibling(this)).onWatchEnabledChanged(isChecked);
        crossfadeView.toggle(isChecked, true);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableBackground) {
            boolean enabled = ChanSettings.watchBackground.get();
            setSettingViewVisibility(backgroundTimeout, enabled, true);
            setSettingViewVisibility(notifyMode, enabled, true);
            setSettingViewVisibility(soundMode, enabled, true);
            setSettingViewVisibility(ledMode, enabled, true);
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

    public interface WatchSettingControllerListener {
        public void onWatchEnabledChanged(boolean enabled);
    }
}
