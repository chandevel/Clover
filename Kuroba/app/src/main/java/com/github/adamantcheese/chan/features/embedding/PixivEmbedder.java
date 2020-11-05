package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;

public class PixivEmbedder
        extends HtmlEmbedder {

    private final Pattern PIXIV_PATTERN =
            Pattern.compile("https?://(?:www\\.)?pixiv\\.net/(?:en/)?artworks/(\\d+)(?:/|\\b)");

    @Override
    public List<CharSequence> getShortRepresentations() {
        return Collections.singletonList("pixiv");
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
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addStandardEmbedCalls(this, theme, post);
    }

    @Override
    public EmbeddingEngine.EmbedResult process(Document response) {
        String generatedURL = response.select("p>img").get(0).attr("src");
        String fullsizeUrl = generatedURL.replaceAll("/c/\\d+x\\d+/", "/");
        String serverName = generatedURL.substring(generatedURL.lastIndexOf('/') + 1);

        return new EmbeddingEngine.EmbedResult(
                response.select("p>img").get(0).attr("alt"),
                "",
                new PostImage.Builder().serverFilename(serverName)
                        .thumbnailUrl(HttpUrl.get(generatedURL))
                        .imageUrl(HttpUrl.get(fullsizeUrl)) // this isn't the "source" as it's always a JPG, but it's good enough
                        .filename(Parser.unescapeEntities(response.select("a>h1").get(0).html(), false))
                        .extension(StringUtils.extractFileNameExtension(fullsizeUrl))
                        .isInlined()
                        .build()
        );
    }
}
