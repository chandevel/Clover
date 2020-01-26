package com.github.adamantcheese.chan.ui.controller.settings;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.controller.AdjustAndroid10GestureZonesController;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
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

        requiresUiRefresh.add(group.add(new LinkSettingView(
                this,
                // TODO(gestures): strings!
                "Android 10 gesture zones",
                "Adjust zones where new Android 10 gestures will be disabled",
                (v) -> {
                    AdjustAndroid10GestureZonesController adjustGestureZonesController
                            = new AdjustAndroid10GestureZonesController(context);

                    navigationController.presentController(adjustGestureZonesController);
                }
        )));

        groups.add(group);
    }
}
