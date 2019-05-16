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

public class SharedPreferencesSettingProvider implements SettingProvider {
    private SharedPreferences prefs;

    public SharedPreferencesSettingProvider(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public int getInt(String key, int def) {
        return prefs.getInt(key, def);
    }

    @Override
    public void putInt(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    public void putIntSync(String key, int value) {
        prefs.edit().putInt(key, value).commit();
    }

    @Override
    public long getLong(String key, long def) {
        return prefs.getLong(key, def);
    }

    @Override
    public void putLong(String key, long value) {
        prefs.edit().putLong(key, value).apply();
    }

    public void putLongSync(String key, long value) {
        prefs.edit().putLong(key, value).commit();
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }

    public void putBooleanSync(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }

    @Override
    public String getString(String key, String def) {
        return prefs.getString(key, def);
    }

    @Override
    public void putString(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    public void putStringSync(String key, String value) {
        prefs.edit().putString(key, value).commit();
    }
}
