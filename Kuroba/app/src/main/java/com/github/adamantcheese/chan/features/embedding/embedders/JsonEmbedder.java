package com.github.adamantcheese.chan.features.embedding.embedders;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;

import okhttp3.Response;

public abstract class JsonEmbedder
        implements Embedder {

    @Override
    public EmbedResult convert(Response response)
            throws Exception {
        return new NetUtilsClasses.ChainConverter<>(getInternalConverter()).chain(NetUtilsClasses.JSON_CONVERTER)
                .convert(response);
    }

    public abstract NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter();
}
