package org.floens.chan.ui.preferences;

import android.view.View;

import org.floens.chan.R;

public class LinkSettingView extends SettingView {
    private final View.OnClickListener clickListener;
    private final String description;

    public LinkSettingView(SettingsController settingsController, String name, String description, View.OnClickListener clickListener) {
        super(settingsController, name);
        this.description = description;
        this.clickListener = clickListener;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(clickListener);
    }

    @Override
    public String getBottomDescription() {
        return description;
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
    }
}
