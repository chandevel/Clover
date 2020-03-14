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

public interface SettingProvider {

    //region Integer
    int getInt(String key, int def);

    void putInt(String key, int value);

    void putIntSync(String key, Integer value);

    //endregion
    //region Long
    long getLong(String key, long def);

    void putLong(String key, long value);

    void putLongSync(String key, Long value);

    //endregion
    //region Boolean
    boolean getBoolean(String key, boolean def);

    void putBoolean(String key, boolean value);

    void putBooleanSync(String key, Boolean value);

    //endregion
    //region String
    String getString(String key, String def);

    void putString(String key, String value);

    void putStringSync(String key, String value);

    //endregion

    void removeSync(String key);
}
