package org.floens.chan.core.settings;

import android.content.SharedPreferences;

public abstract class Setting<T> {
    protected final SharedPreferences sharedPreferences;
    protected final String key;
    protected final T def;

    public Setting(SharedPreferences sharedPreferences, String key, T def) {
        this.sharedPreferences = sharedPreferences;
        this.key = key;
        this.def = def;
    }

    public abstract T get();

    public abstract void set(T value);

    public T getDefault() {
        return def;
    }
}
