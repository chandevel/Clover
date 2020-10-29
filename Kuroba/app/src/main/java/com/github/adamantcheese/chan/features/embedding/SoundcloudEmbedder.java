package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;

public class SoundcloudEmbedder
        extends JsonEmbedder {
    private static final Pattern SOUNDCLOUD_PATTERN =
            Pattern.compile("https?://(?:www\\.)?soundcloud\\.com/.*(?:/|\\b)");

    @Override
    public List<String> getShortRepresentations() {
        return Collections.singletonList("soundcloud");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.soundcloudIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return SOUNDCLOUD_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://soundcloud.com/oembed?format=json&url=" + matcher.group(0));
    }

    /* EXAMPLE JSON
        {
          "version": 1,
          "type": "rich",
          "provider_name": "SoundCloud",
          "provider_url": "https://soundcloud.com",
          "height": 400,
          "width": "100%",
          "title": "Pop Smoke - For The Night (feat. DaBaby & Lil Baby) by POP SMOKE",
          "description": null,
          "thumbnail_url": "https://i1.sndcdn.com/artworks-IMxRfEWmddxz-0-t500x500.jpg",
          "html": "<iframe width=\"100%\" height=\"400\" scrolling=\"no\" frameborder=\"no\" src=\"https://w.soundcloud.com/player/?visual=true&url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F850507126&show_artwork=true\"></iframe>",
          "author_name": "POP SMOKE",
          "author_url": "https://soundcloud.com/biggavelipro"
        }
     */

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addStandardEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult process(JsonReader response)
            throws IOException {
        String title = "Soundcloud Link";
        HttpUrl thumbnailUrl = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
        HttpUrl sourceUrl = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");

        response.beginObject();
        while (response.hasNext()) {
            switch (response.nextName()) {
                case "title":
                    title = response.nextString().replace("by", "|");
                    break;
                case "thumbnail_url":
                    thumbnailUrl = HttpUrl.get(response.nextString());
                    break;
                case "html":
                    String html = response.nextString();
                    Pattern p = Pattern.compile("src=\"(.*)\"");
                    Matcher m = p.matcher(html);
                    if (m.find()) {
                        sourceUrl = HttpUrl.get(m.group(1) + "&color=%23" + getRGBColorIntString(getAttrColor(
                                ThemeHelper.getTheme().accentColor.accentStyleId,
                                R.attr.colorAccent
                        )));
                    }
                    break;
                default:
                    response.skipValue();
                    break;
            }
        }
        response.endObject();

        return new EmbedResult(title,
                "",
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(thumbnailUrl)
                        .imageUrl(sourceUrl)
                        .filename(title)
                        .extension("iframe")
                        .isInlined(true)
                        .build()
        );
    }
}
