package org.floens.chan.ui.controller;

import android.content.Context;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.ui.preferences.LinkSettingView;
import org.floens.chan.ui.preferences.SettingsGroup;
import org.floens.chan.ui.preferences.SettingsController;

public class AdvancedSettingsController extends SettingsController {
    public AdvancedSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_advanced_label);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(string(R.string.settings_advanced_label));
        settings.settingViews.add(new LinkSettingView(this, "Hallo", null, null));

        groups.add(settings);
    }
}
