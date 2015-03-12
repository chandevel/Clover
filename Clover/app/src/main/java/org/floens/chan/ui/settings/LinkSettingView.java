package org.floens.chan.ui.settings;

import android.view.View;

import org.floens.chan.R;

public class LinkSettingView extends SettingView {
    private final View.OnClickListener clickListener;
    private String description;
    private boolean built = false;

    public LinkSettingView(SettingsController settingsController, String name, String description, View.OnClickListener clickListener) {
        super(settingsController, name);
        this.description = description;
        this.clickListener = clickListener;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(clickListener);
        built = true;
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

    public void setDescription(String description) {
        this.description = description;
        if (built) {
            settingsController.onPreferenceChange(this);
        }
    }
}
