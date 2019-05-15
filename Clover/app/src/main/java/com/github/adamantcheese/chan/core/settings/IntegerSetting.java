/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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

public class IntegerSetting extends Setting<Integer> {
    private boolean hasCached = false;
    private Integer cached;

    public IntegerSetting(SettingProvider settingProvider, String key, Integer def) {
        super(settingProvider, key, def);
    }

    @Override
    public Integer get() {
        if (hasCached) {
            return cached;
        } else {
            cached = settingProvider.getInt(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(Integer value) {
        if (!value.equals(get())) {
            settingProvider.putInt(key, value);
            cached = value;
            onValueChanged();
        }
    }
}
