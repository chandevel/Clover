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
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

public class VimeoEmbedder
        extends JsonEmbedder {
    private final Pattern VIMEO_PATTERN = Pattern.compile("https?://(?:www\\.)?vimeo\\.com/\\d+(?:/|\\b)");

    @Override
    public List<CharSequence> getShortRepresentations() {
        return Collections.singletonList("vimeo");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.vimeoIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return VIMEO_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://vimeo.com/api/oembed.json?color="
                + getRGBColorIntString(getAttrColor(ThemeHelper.getTheme().accentColor.accentStyleId,
                R.attr.colorAccent
        )) + "&url=" + matcher.group(0));
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addStandardEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult process(JsonReader response)
            throws IOException {
        String title = "Vimeo Link";
        String duration = "";
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
                case "duration":
                    duration = prettyPrintDateUtilsElapsedTime(response.nextInt());
                    break;
                default:
                    response.skipValue();
                    break;
            }
        }
        response.endObject();

        return new EmbedResult(title,
                duration,
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(thumbnailUrl)
                        .imageUrl(sourceUrl)
                        .filename(title)
                        .extension("iframe")
                        .isInlined()
                        .build()
        );
    }
}
