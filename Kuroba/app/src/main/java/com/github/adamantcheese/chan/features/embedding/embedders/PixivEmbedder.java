package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.common.io.Files;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class PixivEmbedder
        extends HtmlEmbedder {

    private final Pattern PIXIV_PATTERN =
            Pattern.compile("https?://(?:www\\.)?pixiv\\.net/(?:en/)?artworks/(\\d+)(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "pixiv");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.pixivIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return PIXIV_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://embed.pixiv.net/embed_mk2.php?id=" + matcher.group(1));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, Document> getInternalConverter() {
        return input -> {
            String generatedURL = input.select("p>img").get(0).attr("src");
            String fullsizeUrl = generatedURL.replaceAll("/c/\\d+x\\d+/", "/");
            String serverName = generatedURL.substring(generatedURL.lastIndexOf('/') + 1);

            return new EmbedResult(
                    input.select("p>img").get(0).attr("alt"),
                    "",
                    new PostImage.Builder().serverFilename(serverName)
                            .thumbnailUrl(HttpUrl.get(generatedURL))
                            .imageUrl(HttpUrl.get(fullsizeUrl)) // this isn't the "source" as it's always a JPG, but it's good enough
                            .filename(Parser.unescapeEntities(input.select("a>h1").get(0).html(), false))
                            .extension(Files.getFileExtension(fullsizeUrl))
                            .isInlined()
                            .build()
            );
        };
    }
}
