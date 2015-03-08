package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class StringSetting extends Setting<String> {
    public StringSetting(SharedPreferences sharedPreferences, String key, String def) {
        super(sharedPreferences, key, def);
    }

    @Override
    public String get() {
        return sharedPreferences.getString(key, def);
    }

    @Override
    public void set(String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }
}
