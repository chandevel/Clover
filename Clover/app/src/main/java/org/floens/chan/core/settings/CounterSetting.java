package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public class CounterSetting extends IntegerSetting {
    public CounterSetting(SharedPreferences sharedPreferences, String key) {
        super(sharedPreferences, key, 0);
    }

    public CounterSetting(SharedPreferences sharedPreferences, String key, SettingCallback<Integer> callback) {
        super(sharedPreferences, key, 0, callback);
    }

    public int increase() {
        set(get() + 1);
        return get();
    }
}
