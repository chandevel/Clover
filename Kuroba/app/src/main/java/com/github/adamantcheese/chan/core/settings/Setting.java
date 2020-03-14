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
package com.github.adamantcheese.chan.core.settings;

import java.util.ArrayList;
import java.util.List;

public abstract class Setting<T> {
    protected final SettingProvider settingProvider;
    protected final String key;
    protected final T def;
    private List<SettingCallback<T>> callbacks = new ArrayList<>();

    public Setting(SettingProvider settingProvider, String key, T def) {
        this.settingProvider = settingProvider;
        this.key = key;
        this.def = def;
    }

    public abstract T get();

    public abstract void set(T value);

    public abstract void setSync(T value);

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
        for (SettingCallback<T> callback : callbacks) {
            callback.onValueChange(this, get());
        }
    }

    public interface SettingCallback<T> {
        void onValueChange(Setting setting, T value);
    }
}
