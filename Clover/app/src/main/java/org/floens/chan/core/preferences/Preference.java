package org.floens.chan.core.preferences;

import android.content.SharedPreferences;

public abstract class Preference<T> {
    protected final SharedPreferences sharedPreferences;
    protected final String key;
    protected final T def;

    public Preference(SharedPreferences sharedPreferences, String key, T def) {
        this.sharedPreferences = sharedPreferences;
        this.key = key;
        this.def = def;
    }

    public abstract T get();

    public abstract void set(T value);
}
