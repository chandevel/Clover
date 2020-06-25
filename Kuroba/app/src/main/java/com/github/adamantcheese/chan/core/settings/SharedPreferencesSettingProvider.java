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

import android.content.SharedPreferences;

public class SharedPreferencesSettingProvider
        implements SettingProvider<Object> {
    private SharedPreferences prefs;

    public SharedPreferencesSettingProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public Object getValue(String key, Object def) {
        if (def instanceof Integer) {
            return (Integer) prefs.getInt(key, (Integer) def);
        } else if (def instanceof Long) {
            return (Long) prefs.getLong(key, (Long) def);
        } else if (def instanceof Boolean) {
            return (Boolean) prefs.getBoolean(key, (Boolean) def);
        } else if (def instanceof String) {
            return (String) prefs.getString(key, (String) def);
        } else {
            throw new UnsupportedOperationException("Needs a handler for type " + def.getClass().getSimpleName());
        }
    }

    @Override
    public void putValue(String key, Object value) {
        if (value instanceof Integer) {
            prefs.edit().putInt(key, (Integer) value).apply();
        } else if (value instanceof Long) {
            prefs.edit().putLong(key, (Long) value).apply();
        } else if (value instanceof Boolean) {
            prefs.edit().putBoolean(key, (Boolean) value).apply();
        } else if (value instanceof String) {
            prefs.edit().putString(key, (String) value).apply();
        } else {
            throw new UnsupportedOperationException("Needs a handler for type " + value.getClass().getSimpleName());
        }
    }

    @Override
    public void putValueSync(String key, Object value) {
        if (value instanceof Integer) {
            prefs.edit().putInt(key, (Integer) value).commit();
        } else if (value instanceof Long) {
            prefs.edit().putLong(key, (Long) value).commit();
        } else if (value instanceof Boolean) {
            prefs.edit().putBoolean(key, (Boolean) value).commit();
        } else if (value instanceof String) {
            prefs.edit().putString(key, (String) value).commit();
        } else {
            throw new UnsupportedOperationException("Needs a handler for type " + value.getClass().getSimpleName());
        }
    }

    @Override
    public void removeSync(String key) {
        prefs.edit().remove(key).commit();
    }
}
