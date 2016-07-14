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

import java.util.ArrayList;
import java.util.List;

public abstract class Setting<T> {
    protected final SharedPreferences sharedPreferences;
    protected final String key;
    protected final T def;
    private List<SettingCallback<T>> callbacks = new ArrayList<>();

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

    public String getKey() {
        return key;
    }

    public void addCallback(SettingCallback<T> callback) {
        this.callbacks.add(callback);
    }

    public void removeCallback(SettingCallback<T> callback) {
        this.callbacks.remove(callback);
    }

    protected final void onValueChanged() {
        for (int i = 0; i < callbacks.size(); i++) {
            callbacks.get(i).onValueChange(this, get());
        }
    }

    public interface SettingCallback<T> {
        void onValueChange(Setting setting, T value);
    }
}
