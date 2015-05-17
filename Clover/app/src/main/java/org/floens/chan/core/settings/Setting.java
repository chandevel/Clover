/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
