package org.floens.chan.ui.settings;

import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;

import org.floens.chan.R;
import org.floens.chan.core.settings.Setting;

public class BooleanSettingView extends SettingView implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {
    private SwitchCompat switcher;
    private Setting<Boolean> setting;
    private String description;
    private boolean building = true;

    public BooleanSettingView(SettingsController settingsController, Setting<Boolean> setting, String name, String description) {
        super(settingsController, name);
        this.setting = setting;
        this.description = description;
    }

    @Override
    public void setView(View view) {
        super.setView(view);

        view.setOnClickListener(this);

        switcher = (SwitchCompat) view.findViewById(R.id.switcher);
        switcher.setOnCheckedChangeListener(this);

        switcher.setChecked(setting.get());

        building = false;
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
        switcher.setEnabled(enabled);
    }

    @Override
    public void onClick(View v) {
        switcher.toggle();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (!building) {
            setting.set(isChecked);
            settingsController.onPreferenceChange(this);
        }
    }
}
