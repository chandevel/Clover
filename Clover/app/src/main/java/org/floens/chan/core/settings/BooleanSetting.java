package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class BooleanSetting extends Setting<Boolean> {
    private boolean hasCached = false;
    private boolean cached;

    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def) {
        super(sharedPreferences, key, def);
    }

    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def, SettingCallback<Boolean> callback) {
        super(sharedPreferences, key, def, callback);
    }

    @Override
    public Boolean get() {
        if (hasCached) {
            return cached;
        } else {
            cached = sharedPreferences.getBoolean(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(Boolean value) {
        if (!value.equals(get())) {
            sharedPreferences.edit().putBoolean(key, value).apply();
            cached = value;
            onValueChanged();
        }
    }
}
