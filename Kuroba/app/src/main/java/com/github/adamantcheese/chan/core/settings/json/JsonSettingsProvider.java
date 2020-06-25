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
package com.github.adamantcheese.chan.core.settings.json;

import com.github.adamantcheese.chan.core.settings.SettingProvider;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings.JsonSetting;

public class JsonSettingsProvider
        implements SettingProvider<Object> {
    public final JsonSettings jsonSettings;
    private Callback callback;

    public JsonSettingsProvider(JsonSettings jsonSettings, Callback callback) {
        this.jsonSettings = jsonSettings;
        this.callback = callback;
    }

    @Override
    public Object getValue(String key, Object def) {
        JsonSetting<?> setting = jsonSettings.settings.get(key);
        if (setting == null) return def;
        return setting.value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void putValue(String key, Object value) {
        JsonSetting<?> jsonSetting = jsonSettings.settings.get(key);
        if (value instanceof Integer) {
            if (jsonSetting == null) {
                JsonSetting<Integer> v = new JsonSetting<>();
                v.value = (Integer) value;
                jsonSettings.settings.put(key, v);
            } else {
                ((JsonSetting<Integer>) jsonSetting).value = (Integer) value;
            }
        } else if (value instanceof Long) {
            if (jsonSetting == null) {
                JsonSetting<Long> v = new JsonSetting<>();
                v.value = (Long) value;
                jsonSettings.settings.put(key, v);
            } else {
                ((JsonSetting<Long>) jsonSetting).value = (Long) value;
            }
        } else if (value instanceof Boolean) {
            if (jsonSetting == null) {
                JsonSetting<Boolean> v = new JsonSetting<>();
                v.value = (Boolean) value;
                jsonSettings.settings.put(key, v);
            } else {
                ((JsonSetting<Boolean>) jsonSetting).value = (Boolean) value;
            }
        } else if (value instanceof String) {
            if (jsonSetting == null) {
                JsonSetting<String> v = new JsonSetting<>();
                v.value = (String) value;
                jsonSettings.settings.put(key, v);
            } else {
                ((JsonSetting<String>) jsonSetting).value = (String) value;
            }
        } else {
            throw new UnsupportedOperationException("Needs a handler for type " + value.getClass().getSimpleName());
        }
        callback.save();
    }

    @Override
    public void putValueSync(String key, Object value) {
        putValue(key, value);
    }

    @Override
    public void removeSync(String key) {
        jsonSettings.settings.remove(key);
    }

    public interface Callback {
        void save();
    }
}
