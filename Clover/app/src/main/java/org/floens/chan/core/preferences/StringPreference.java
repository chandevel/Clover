package org.floens.chan.core.preferences;

import android.content.SharedPreferences;

public class StringPreference extends Preference<String> {
    public StringPreference(SharedPreferences sharedPreferences, String key, String def) {
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
