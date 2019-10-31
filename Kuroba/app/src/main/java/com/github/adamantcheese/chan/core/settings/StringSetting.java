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

public class StringSetting extends Setting<String> {
    private boolean hasCached = false;
    private String cached;

    public StringSetting(SettingProvider settingProvider, String key, String def) {
        super(settingProvider, key, def);
    }

    @Override
    public String get() {
        if (hasCached) {
            return cached;
        } else {
            cached = settingProvider.getString(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(String value) {
        if (!value.equals(get())) {
            settingProvider.putString(key, value);
            cached = value;
            onValueChanged();
        }
    }

    public void setNoUpdate(String value) {
        if (!value.equals(get())) {
            settingProvider.putString(key, value);
            cached = value;
        }
    }

    public void setSync(String value) {
        if (!value.equals(get())) {
            settingProvider.putStringSync(key, value);
            cached = value;
            onValueChanged();
        }
    }

    public void setSyncNoCheck(String value) {
        settingProvider.putStringSync(key, value);
        cached = value;
        onValueChanged();
    }

    public void remove() {
        settingProvider.removeSync(key);
        hasCached = false;
        cached = null;
    }
}
