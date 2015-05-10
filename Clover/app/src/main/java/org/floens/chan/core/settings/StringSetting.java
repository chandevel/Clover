package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class StringSetting extends Setting<String> {
    private boolean hasCached = false;
    private String cached;

    public StringSetting(SharedPreferences sharedPreferences, String key, String def) {
        super(sharedPreferences, key, def);
    }

    public StringSetting(SharedPreferences sharedPreferences, String key, String def, SettingCallback<String> callback) {
        super(sharedPreferences, key, def, callback);
    }

    @Override
    public String get() {
        if (hasCached) {
            return cached;
        } else {
            cached = sharedPreferences.getString(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(String value) {
        if (!value.equals(get())) {
            sharedPreferences.edit().putString(key, value).apply();
            cached = value;
            onValueChanged();
        }
    }
}
