package com.github.adamantcheese.chan.features.embedding;

import okhttp3.HttpUrl;

public class EmbedNoTitleException
        extends Exception {
    public EmbedNoTitleException() {
        super("No title found!");
    }

    public EmbedNoTitleException(HttpUrl url) {
        super("No title for " + url);
    }
}
