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
package org.floens.chan.core.model.orm;

import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.settings.json.BooleanJsonSetting;
import org.floens.chan.core.settings.json.IntegerJsonSetting;
import org.floens.chan.core.settings.json.JsonSetting;
import org.floens.chan.core.settings.json.JsonSettings;
import org.floens.chan.core.settings.json.LongJsonSetting;
import org.floens.chan.core.settings.json.RuntimeTypeAdapterFactory;
import org.floens.chan.core.settings.json.StringJsonSetting;
import org.floens.chan.utils.Logger;

@DatabaseTable(tableName = "site")
public class SiteModel {
    private static final Gson gson;

    static {
        RuntimeTypeAdapterFactory<JsonSetting> userSettingAdapter =
                RuntimeTypeAdapterFactory.of(JsonSetting.class, "type")
                        .registerSubtype(StringJsonSetting.class, "string")
                        .registerSubtype(IntegerJsonSetting.class, "integer")
                        .registerSubtype(LongJsonSetting.class, "long")
                        .registerSubtype(BooleanJsonSetting.class, "boolean");

        gson = new GsonBuilder()
                .registerTypeAdapterFactory(userSettingAdapter)
                .create();
    }

    @DatabaseField(generatedId = true, allowGeneratedIdInsert = true)
    public int id;

    @DatabaseField
    public String configuration;

    @DatabaseField
    public String userSettings;

    @DatabaseField
    public int order;

    public SiteModel() {
    }

    public void storeConfig(SiteConfig config) {
        this.configuration = gson.toJson(config);
    }

    public void storeUserSettings(JsonSettings userSettings) {
        this.userSettings = gson.toJson(userSettings);
        Logger.test("userSettings = " + this.userSettings);
    }

    public Pair<SiteConfig, JsonSettings> loadConfigFields() {
        SiteConfig config = gson.fromJson(this.configuration, SiteConfig.class);
        JsonSettings settings = gson.fromJson(this.userSettings, JsonSettings.class);
        Logger.d("SiteModel", "Config: " + configuration + ", Settings: " + userSettings);
        return Pair.create(config, settings);
    }
}
