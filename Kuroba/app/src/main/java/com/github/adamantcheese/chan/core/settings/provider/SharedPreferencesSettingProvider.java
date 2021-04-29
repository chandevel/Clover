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
package com.github.adamantcheese.chan.core.settings.provider;

import android.content.SharedPreferences;

public class SharedPreferencesSettingProvider
        implements SettingProvider<Object> {
    private final SharedPreferences prefs;

    public SharedPreferencesSettingProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public Object getValue(String key, Object def) {
        Object ret;
        try {
            if (def instanceof Integer) {
                ret = prefs.getInt(key, (Integer) def);
            } else if (def instanceof Long) {
                ret = prefs.getLong(key, (Long) def);
            } else if (def instanceof Boolean) {
                ret = prefs.getBoolean(key, (Boolean) def);
            } else if (def instanceof String) {
                ret = prefs.getString(key, (String) def);
            } else {
                throw new UnsupportedOperationException("Needs a handler for type " + def.getClass().getSimpleName());
            }
            if (!prefs.contains(key)) {
                // if a preference doesn't exist, put it first before returning the default so it does exist
                putValueSync(key, def);
            }
            return ret;
        } catch (ClassCastException e) {
            return def;
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
