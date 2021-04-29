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
package com.github.adamantcheese.chan.core.settings.primitives;

import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;

public class OptionsSetting<T extends Enum<?> & OptionSettingItem>
        extends Setting<T> {
    private final T[] items;

    public OptionsSetting(SettingProvider<Object> settingProvider, String key, Class<T> clazz, T def) {
        super(settingProvider, key, def);

        this.items = clazz.getEnumConstants();
    }

    public T[] getItems() {
        return items;
    }

    @Override
    public T get() {
        if (cached == null) {
            String itemName = (String) settingProvider.getValue(key, def.getKey());
            T selectedItem = null;
            for (T item : items) {
                if (item.getKey().equals(itemName)) {
                    selectedItem = item;
                }
            }
            if (selectedItem == null) {
                selectedItem = def;
            }

            cached = selectedItem;
        }
        return cached;
    }

    @Override
    public void set(T value) {
        if (!value.equals(get())) {
            settingProvider.putValue(key, value.getKey());
            cached = value;
            onValueChanged();
        }
    }

    @Override
    public void setSync(T value) {
        if (!value.equals(get())) {
            settingProvider.putValueSync(key, value.getKey());
            cached = value;
            onValueChanged();
        }
    }

    @Override
    public void remove() {
        settingProvider.removeSync(key);
        cached = null;
        onValueChanged();
    }
}
