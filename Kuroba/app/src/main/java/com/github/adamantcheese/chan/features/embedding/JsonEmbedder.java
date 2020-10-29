package com.github.adamantcheese.chan.features.embedding;

import android.util.JsonReader;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.utils.NetUtilsClasses;

import okhttp3.ResponseBody;

public abstract class JsonEmbedder
        implements Embedder<JsonReader> {

    @Override
    public JsonReader convert(@Nullable ResponseBody body)
            throws Exception {
        return new NetUtilsClasses.JSONConverter().convert(body);
    }
}
