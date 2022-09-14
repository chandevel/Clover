package com.github.adamantcheese.chan.features.embedding.embedders;

import static com.github.adamantcheese.chan.utils.BuildConfigUtils.INTERNAL_SPOILER_THUMB_URL;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedNoTitleException;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class VimeoEmbedder
        extends JsonEmbedder {
    private final Pattern VIMEO_PATTERN = Pattern.compile("https?://(?:www\\.)?vimeo\\.com/\\d+(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "vimeo");
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
                + getRGBColorIntString(ThemeHelper.getTheme().accentColorInt)
                + "&url="
                + matcher.group(0));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            String title = null;
            String duration = null;
            HttpUrl thumbnailUrl = INTERNAL_SPOILER_THUMB_URL;
            HttpUrl sourceUrl = INTERNAL_SPOILER_THUMB_URL;

            input.beginObject();
            while (input.hasNext()) {
                switch (input.nextName()) {
                    case "title":
                        title = input.nextString().replace("by", "|");
                        break;
                    case "thumbnail_url":
                        thumbnailUrl = HttpUrl.get(input.nextString());
                        break;
                    case "html":
                        String html = input.nextString();
                        Pattern p = Pattern.compile("src=\"(.*)\"");
                        Matcher m = p.matcher(html);
                        if (m.find()) {
                            sourceUrl = HttpUrl.get(m.group(1)
                                    + "&color=%23"
                                    + getRGBColorIntString(ThemeHelper.getTheme().accentColorInt));
                        }
                        break;
                    case "duration":
                        duration = prettyPrintDateUtilsElapsedTime(input.nextInt());
                        break;
                    default:
                        input.skipValue();
                        break;
                }
            }
            input.endObject();

            if (title == null) throw new EmbedNoTitleException();

            return new EmbedResult(
                    title,
                    duration,
                    new PostImage.Builder()
                            .serverFilename(title)
                            .thumbnailUrl(thumbnailUrl)
                            .imageUrl(sourceUrl)
                            .filename(title)
                            .extension("iframe")
                            .isInlined()
                            .build()
            );
        };
    }
}
