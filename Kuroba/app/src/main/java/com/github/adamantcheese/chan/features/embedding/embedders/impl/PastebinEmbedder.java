package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import android.graphics.Bitmap;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.HtmlEmbedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Document;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class PastebinEmbedder
        extends HtmlEmbedder {
    public static final Pattern PASTEBIN_LINK_PATTERN = Pattern.compile("https?://pastebin.com/(\\w{8})");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "pastebin");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.pastebinIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return PASTEBIN_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://pastebin.com/" + matcher.group(1));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, Document> getInternalConverter() {
        return input -> new EmbedResult(input.title().replace(" - Pastebin.com", ""), null, Collections.emptyList());
    }
}
