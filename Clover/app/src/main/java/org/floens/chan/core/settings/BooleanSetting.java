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

public class BooleanSetting extends Setting<Boolean> {
    private boolean hasCached = false;
    private boolean cached;

    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def) {
        super(sharedPreferences, key, def);
    }

    public BooleanSetting(SharedPreferences sharedPreferences, String key, Boolean def, SettingCallback<Boolean> callback) {
        super(sharedPreferences, key, def, callback);
    }

    @Override
    public Boolean get() {
        if (hasCached) {
            return cached;
        } else {
            cached = sharedPreferences.getBoolean(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(Boolean value) {
        if (!value.equals(get())) {
            sharedPreferences.edit().putBoolean(key, value).apply();
            cached = value;
            onValueChanged();
        }
    }
}
