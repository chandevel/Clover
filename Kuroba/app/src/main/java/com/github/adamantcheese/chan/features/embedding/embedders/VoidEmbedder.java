package com.github.adamantcheese.chan.features.embedding.embedders;

import com.github.adamantcheese.chan.features.embedding.EmbedResult;

import okhttp3.Response;

public abstract class VoidEmbedder
        implements Embedder {

    @Override
    public EmbedResult convert(Response response) {
        return null;
    }
}
