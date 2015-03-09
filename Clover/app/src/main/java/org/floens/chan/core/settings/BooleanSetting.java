package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def) {
        super(sharedPreferences, key, def);
    }

    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def, SettingCallback<Boolean> callback) {
        super(sharedPreferences, key, def, callback);
    }

    @Override
    public Boolean get() {
        return sharedPreferences.getBoolean(key, def);
    }

    @Override
    public void set(Boolean value) {
        if (!value.equals(get())) {
            sharedPreferences.edit().putBoolean(key, value).apply();
            onValueChanged();
        }
    }
}
