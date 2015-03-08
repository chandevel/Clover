package org.floens.chan.ui.preferences;

import java.util.ArrayList;
import java.util.List;

public class SettingsGroup {
    public final String name;
    public final List<SettingView> settingViews = new ArrayList<>();

    public SettingsGroup(String name) {
        this.name = name;
    }

    public SettingView add(SettingView settingView) {
        settingViews.add(settingView);
        return settingView;
    }
}
