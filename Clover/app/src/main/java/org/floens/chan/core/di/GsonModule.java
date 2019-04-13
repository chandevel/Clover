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
