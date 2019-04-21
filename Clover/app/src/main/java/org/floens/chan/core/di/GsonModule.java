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
package org.floens.chan.core.di;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.codejargon.feather.Provides;
import org.floens.chan.core.settings.json.BooleanJsonSetting;
import org.floens.chan.core.settings.json.IntegerJsonSetting;
import org.floens.chan.core.settings.json.JsonSetting;
import org.floens.chan.core.settings.json.LongJsonSetting;
import org.floens.chan.core.settings.json.RuntimeTypeAdapterFactory;
import org.floens.chan.core.settings.json.StringJsonSetting;

import javax.inject.Singleton;

public class GsonModule {

    @Provides
    @Singleton
    public Gson provideGson() {
        RuntimeTypeAdapterFactory<JsonSetting> userSettingAdapter =
                RuntimeTypeAdapterFactory.of(JsonSetting.class, "type")
                        .registerSubtype(StringJsonSetting.class, "string")
                        .registerSubtype(IntegerJsonSetting.class, "integer")
                        .registerSubtype(LongJsonSetting.class, "long")
                        .registerSubtype(BooleanJsonSetting.class, "boolean");

        return new GsonBuilder()
                .registerTypeAdapterFactory(userSettingAdapter)
                .create();
    }
}
