package org.floens.chan.ui.preferences;

import android.view.View;

public abstract class PreferenceItem {
    public PreferencesController preferencesController;
    public final String name;
    public View view;

    public PreferenceItem(PreferencesController preferencesController, String name) {
        this.preferencesController = preferencesController;
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
