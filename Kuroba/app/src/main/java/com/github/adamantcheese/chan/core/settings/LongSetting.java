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

public class LongSetting
        extends Setting<Long> {
    private boolean hasCached = false;
    private Long cached;

    public LongSetting(SettingProvider settingProvider, String key, Long def) {
        super(settingProvider, key, def);
    }

    @Override
    public Long get() {
        if (hasCached) {
            return cached;
        } else {
            cached = settingProvider.getLong(key, def);
            hasCached = true;
            return cached;
        }
    }

    @Override
    public void set(Long value) {
        if (!value.equals(get())) {
            settingProvider.putLong(key, value);
            cached = value;
            onValueChanged();
        }
    }

    public void setSync(Long value) {
        if (!value.equals(get())) {
            settingProvider.putLongSync(key, value);
            cached = value;
            onValueChanged();
        }
    }
}
