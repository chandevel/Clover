package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(SharedPreferences sharedPreferences, String key, boolean def) {
        super(sharedPreferences, key, def);
    }

    @Override
    public Boolean get() {
        return sharedPreferences.getBoolean(key, def);
    }

    @Override
    public void set(Boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }
}
