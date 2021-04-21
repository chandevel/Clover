package com.github.adamantcheese.chan.features.embedding.embedders;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.Converter;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;

import org.jsoup.nodes.Document;

import okhttp3.Response;

public abstract class HtmlEmbedder
        implements Embedder {

    @Override
    public EmbedResult convert(Response response)
            throws Exception {
        return new NetUtilsClasses.ChainConverter<>(getInternalConverter()).chain(NetUtilsClasses.HTML_CONVERTER)
                .convert(response);
    }

    public abstract Converter<EmbedResult, Document> getInternalConverter();
}
