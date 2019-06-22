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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class JsonSettingsProvider implements SettingProvider {
    public final JsonSettings jsonSettings;
    private Callback callback;

    private Map<String, JsonSetting> byKey = new HashMap<>();

    public JsonSettingsProvider(JsonSettings jsonSettings, Callback callback) {
        this.jsonSettings = jsonSettings;
        this.callback = callback;

        load();
    }

    @Override
    public int getInt(String key, int def) {
        JsonSetting setting = byKey.get(key);
        if (setting != null) {
            return ((IntegerJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putInt(String key, int value) {
        JsonSetting jsonSetting = byKey.get(key);
        if (jsonSetting == null) {
            IntegerJsonSetting v = new IntegerJsonSetting();
            v.value = value;
            byKey.put(key, v);
        } else {
            ((IntegerJsonSetting) jsonSetting).value = value;
        }
        save();
    }

    @Override
    public long getLong(String key, long def) {
        JsonSetting setting = byKey.get(key);
        if (setting != null) {
            return ((LongJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putLong(String key, long value) {
        JsonSetting jsonSetting = byKey.get(key);
        if (jsonSetting == null) {
            LongJsonSetting v = new LongJsonSetting();
            v.value = value;
            byKey.put(key, v);
        } else {
            ((LongJsonSetting) jsonSetting).value = value;
        }
        save();
    }

    @Override
    public boolean getBoolean(String key, boolean def) {
        JsonSetting setting = byKey.get(key);
        if (setting != null) {
            return ((BooleanJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        JsonSetting jsonSetting = byKey.get(key);
        if (jsonSetting == null) {
            BooleanJsonSetting v = new BooleanJsonSetting();
            v.value = value;
            byKey.put(key, v);
        } else {
            ((BooleanJsonSetting) jsonSetting).value = value;
        }
        save();
    }

    @Override
    public void putBooleanSync(String key, boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getString(String key, String def) {
        JsonSetting setting = byKey.get(key);
        if (setting != null) {
            return ((StringJsonSetting) setting).value;
        } else {
            return def;
        }
    }

    @Override
    public void putString(String key, String value) {
        JsonSetting jsonSetting = byKey.get(key);
        if (jsonSetting == null) {
            StringJsonSetting v = new StringJsonSetting();
            v.value = value;
            byKey.put(key, v);
        } else {
            ((StringJsonSetting) jsonSetting).value = value;
        }
        save();
    }

    @Override
    public void putStringSync(String key, String value) {
        throw new UnsupportedOperationException();
    }

    private void load() {
        byKey.clear();
        for (JsonSetting setting : jsonSettings.settings) {
            byKey.put(setting.key, setting);
        }
    }

    private void save() {
        List<JsonSetting> settings = new ArrayList<>();
        for (Map.Entry<String, JsonSetting> e : byKey.entrySet()) {
            settings.add(e.getValue());
        }
        jsonSettings.settings = settings;
        callback.save();
    }

    public interface Callback {
        void save();
    }
}
