package com.github.adamantcheese.chan.features.embedding.embedders.base;

import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.Embedder;

import okhttp3.Response;

public abstract class VoidEmbedder
        implements Embedder {

    @Override
    public EmbedResult convert(Response response) {
        return null;
    }
}
