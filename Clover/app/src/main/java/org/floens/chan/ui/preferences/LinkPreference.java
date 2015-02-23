package org.floens.chan.ui.preferences;

import android.view.View;

public class LinkPreference extends PreferenceItem {
    private final View.OnClickListener clickListener;

    public LinkPreference(PreferencesController preferencesController, String name, View.OnClickListener clickListener) {
        super(preferencesController, name);
        this.clickListener = clickListener;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(clickListener);
    }

    @Override
    public void setEnabled(boolean enabled) {

    }
}
