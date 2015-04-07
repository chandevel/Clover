package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public abstract class Setting<T> {
    protected final SharedPreferences sharedPreferences;
    protected final String key;
    protected final T def;
    private SettingCallback<T> callback;

    public Setting(SharedPreferences sharedPreferences, String key, T def) {
        this(sharedPreferences, key, def, null);
    }

    public Setting(SharedPreferences sharedPreferences, String key, T def, SettingCallback<T> callback) {
        this.sharedPreferences = sharedPreferences;
        this.key = key;
        this.def = def;
        this.callback = callback;
    }

    public abstract T get();

    public abstract void set(T value);

    public T getDefault() {
        return def;
    }

    protected final void onValueChanged() {
        if (callback != null) {
            callback.onValueChange(this, get());
        }
    }

    public interface SettingCallback<T> {
        void onValueChange(Setting setting, T value);
    }
}
