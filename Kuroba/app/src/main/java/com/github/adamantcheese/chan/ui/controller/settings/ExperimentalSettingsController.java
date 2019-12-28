package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

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

        groups.add(group);
    }
}
