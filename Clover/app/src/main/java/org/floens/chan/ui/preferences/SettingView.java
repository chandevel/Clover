package org.floens.chan.ui.preferences;

import android.view.View;

public abstract class SettingView {
    public SettingsController settingsController;
    public final String name;
    public View view;

    public SettingView(SettingsController settingsController, String name) {
        this.settingsController = settingsController;
        this.name = name;
    }

    public void setView(View view) {
        this.view = view;
    }

    public void setEnabled(boolean enabled) {
    }

    public String getTopDescription() {
        return name;
    }

    public String getBottomDescription() {
        return null;
    }
}
