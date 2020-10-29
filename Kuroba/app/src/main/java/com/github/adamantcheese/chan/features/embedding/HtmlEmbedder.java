package com.github.adamantcheese.chan.features.embedding;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.utils.NetUtilsClasses;

import org.jsoup.nodes.Document;

import okhttp3.ResponseBody;

public abstract class HtmlEmbedder
        implements Embedder<Document> {

    @Override
    public Document convert(@Nullable ResponseBody body)
            throws Exception {
        return new NetUtilsClasses.HTMLConverter().convert(body);
    }
}
