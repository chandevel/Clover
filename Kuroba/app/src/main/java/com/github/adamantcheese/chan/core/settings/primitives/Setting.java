/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.settings.primitives;

import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;

import java.util.ArrayList;
import java.util.List;

public abstract class Setting<T> {
    protected final SettingProvider<Object> settingProvider;
    protected final String key;
    protected final T def;
    private final List<SettingCallback<T>> callbacks = new ArrayList<>();

    protected T cached;

    public Setting(SettingProvider<Object> settingProvider, String key, T def) {
        this.settingProvider = settingProvider;
        this.key = key;
        this.def = def;
    }

    public T get() {
        if (cached == null) {
            //noinspection unchecked
            cached = (T) settingProvider.getValue(key, def);
        }
        return cached;
    }

    public void set(T value) {
        if (!value.equals(get())) {
            settingProvider.putValue(key, value);
            cached = value;
            onValueChanged();
        }
    }

    public void setSync(T value) {
        if (!value.equals(get())) {
            settingProvider.putValueSync(key, value);
            cached = value;
            onValueChanged();
        }
    }

    public void setSyncNoCheck(T value) {
        settingProvider.putValueSync(key, value);
        cached = value;
        onValueChanged();
    }

    public void remove() {
        settingProvider.removeSync(key);
        cached = null;
    }

    public T getDefault() {
        return def;
    }

    public void reset() {
        set(def);
    }

    public String getKey() {
        return key;
    }

    public void addCallback(SettingCallback<T> callback) {
        callbacks.add(callback);
    }

    public void removeCallback(SettingCallback<T> callback) {
        callbacks.remove(callback);
    }

    protected final void onValueChanged() {
        for (SettingCallback<T> callback : callbacks) {
            callback.onValueChange(this, get());
        }
    }

    public interface SettingCallback<T> {
        void onValueChange(Setting<T> setting, T value);
    }
}
