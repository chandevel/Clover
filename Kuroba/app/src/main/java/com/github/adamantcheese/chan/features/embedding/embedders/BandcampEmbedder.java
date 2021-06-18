package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Document;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrint8601Time;

public class BandcampEmbedder
        extends HtmlEmbedder {
    // note that for this pattern, we allow for non-album/track links, but in the case of a bandcamp page that links directly
    // to the music overview, the parse will fail, so don't add in a hotlink
    private static final Pattern BANDCAMP_PATTERN =
            Pattern.compile("https?://(?:\\w+\\.)?bandcamp\\.com(?:/.*)?(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "bandcamp");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.bandcampIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return BANDCAMP_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get(matcher.group(0));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, Document> getInternalConverter() {
        return input -> {
            String duration = null;
            PostImage extra = null;
            int curThemeResValue = ThemeHelper.getTheme().resValue;
            try {
                String extractedPlayer = input.select("meta[property=twitter:player]").get(0).attr("content");
                extractedPlayer = extractedPlayer.replace("v=2/", "")
                        .replace("notracklist=true/", "")
                        .replace("twittercard=true/", "")
                        .replaceAll("linkcol=.{6}",
                                "linkcol=" + getRGBColorIntString(getAttrColor(curThemeResValue,
                                        android.R.attr.textColorPrimary
                                )) + "/"
                        ) + "bgcol=" + getRGBColorIntString(getAttrColor(curThemeResValue, R.attr.backcolor))
                        + "/minimal=true/transparent=true/";
                HttpUrl embeddedPlayer = HttpUrl.get(extractedPlayer);

                JsonReader durReader = new JsonReader(new StringReader(input.select("script[type=application/ld+json]")
                        .get(0)
                        .html()));
                durReader.beginObject();
                while (durReader.hasNext()) {
                    switch (durReader.nextName()) {
                        case "track": // for an album
                            durReader.beginObject();
                            while (durReader.hasNext()) {
                                if ("itemListElement".equals(durReader.nextName())) { // track element
                                    durReader.beginArray();
                                    while (durReader.hasNext()) {
                                        durReader.beginObject(); // track data
                                        while (durReader.hasNext()) {
                                            if ("item".equals(durReader.nextName())) {
                                                durReader.beginObject();
                                                while (durReader.hasNext()) {
                                                    if ("duration".equals(durReader.nextName())
                                                            && duration == null) { // only the first track's duration
                                                        duration = prettyPrint8601Time(durReader.nextString());
                                                    } else {
                                                        durReader.skipValue();
                                                    }
                                                }
                                                durReader.endObject();
                                            } else {
                                                durReader.skipValue();
                                            }
                                        }
                                        durReader.endObject();
                                    }
                                    durReader.endArray();
                                } else {
                                    durReader.skipValue();
                                }
                            }
                            durReader.endObject();
                            break;
                        case "duration": // for a track
                            duration = prettyPrint8601Time(durReader.nextString());
                            break;
                        default:
                            durReader.skipValue();
                            break;
                    }
                }
                durReader.endObject();

                extra = new PostImage.Builder().serverFilename(input.title())
                        .thumbnailUrl(HttpUrl.get(input.select("#tralbumArt > .popupImage").get(0).attr("href")))
                        .imageUrl(embeddedPlayer)
                        .filename(input.title())
                        .extension("iframe")
                        .isInlined()
                        .build();
            } catch (Exception ignored) {
                // no player on this page
                duration = "";
            }

            return new EmbedResult(input.title(), duration, extra);
        };
    }
}
