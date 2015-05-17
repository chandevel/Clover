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

public class StringSetting extends Setting<String> {
    private boolean hasCached = false;
    private String cached;

    public StringSetting(SharedPreferences sharedPreferences, String key, String def) {
        super(sharedPreferences, key, def);
    }

    public StringSetting(SharedPreferences sharedPreferences, String key, String def, SettingCallback<String> callback) {
        super(sharedPreferences, key, def, callback);
    }

    @Override
    public String get() {
        if (hasCached) {
            return cached;
        } else {
            cached = sharedPreferences.getString(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(String value) {
        if (!value.equals(get())) {
            sharedPreferences.edit().putString(key, value).apply();
            cached = value;
            onValueChanged();
        }
    }
}
