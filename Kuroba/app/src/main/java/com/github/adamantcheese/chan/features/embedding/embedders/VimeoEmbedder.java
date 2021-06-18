package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

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
                + getRGBColorIntString(getAttrColor(ThemeHelper.getTheme().accentColor.accentStyleId,
                R.attr.colorAccent
        )) + "&url=" + matcher.group(0));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            String title = "Vimeo Link";
            String duration = "";
            HttpUrl thumbnailUrl = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
            HttpUrl sourceUrl = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");

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
                            sourceUrl = HttpUrl.get(m.group(1) + "&color=%23" + getRGBColorIntString(getAttrColor(
                                    ThemeHelper.getTheme().accentColor.accentStyleId,
                                    R.attr.colorAccent
                            )));
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
        };
    }
}
