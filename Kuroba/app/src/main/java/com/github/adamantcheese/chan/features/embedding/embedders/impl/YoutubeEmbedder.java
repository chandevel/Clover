package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedNoTitleException;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.Embedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.StringReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.*;

public class YoutubeEmbedder
        implements Embedder {
    // Group 1 is the video id, Group 2 is any parameters after the ID
    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "https?://(?:youtu\\.be/|\\w+\\.youtube\\.\\w+/.*?(?:v=|\\bembed/|\\bv/|\\bshorts/))([\\w\\-]{11})([^\\s]*)(?:/|\\b)");
    // All the relevant information is hidden away in a var called ytInitialPlayerResponse; we can snag that JSON and use it
    private static final Pattern API_PARAMS =
            Pattern.compile("ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\})\\s*;(?:\\s*v)?");

    @Override
    public void setup(CookieJar cookieJar) {
        List<Cookie> toAdd = new ArrayList<>();
        // this cookie has an expiration date, but we set it here to be forever basically
        toAdd.add(new Cookie.Builder()
                .domain("youtube.com")
                .path("/")
                .secure()
                .name("CONSENT")
                .value("YES+cb.20210615-14-p0.EN-GB+FX")
                .expiresAt(Long.MAX_VALUE)
                .build());
        cookieJar.saveFromResponse(HttpUrl.get("https://www.youtube.com"), toAdd);
    }

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "youtu.be", "youtube");
    }

    @Override
    public int getTimeoutMillis() {
        // extra time for this one since the JSON to parse can be quite large
        return (int) TimeUnit.SECONDS.toMillis(10);
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.youtubeIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return YOUTUBE_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://www.youtube.com/watch?v=" + matcher.group(1) + matcher.group(2));
    }

    @Override
    public EmbedResult convert(Response response)
            throws Exception {
        return new NetUtilsClasses.ChainConverter<EmbedResult, JsonReader>(input -> {
            HttpUrl url = response.request().url();
            String id = null;
            String title = null;
            String duration = null;
            String smallThumb = null;
            input.beginObject();
            while (input.hasNext()) {
                if ("videoDetails".equals(input.nextName())) {
                    input.beginObject();
                    while (input.hasNext()) {
                        switch (input.nextName()) {
                            case "videoId":
                                id = input.nextString();
                                break;
                            case "title":
                                title = URLDecoder.decode(input.nextString(), "UTF-8");
                                break;
                            case "lengthSeconds":
                                duration = prettyPrintDateUtilsElapsedTime(input.nextInt());
                                break;
                            case "thumbnail":
                                smallThumb = getLargestSmallThumb(input);
                                break;
                            case "isLiveContent":
                                if (input.nextBoolean()) {
                                    duration = "[LIVE]";
                                }
                                break;
                            default:
                                input.skipValue();
                                break;
                        }
                    }
                    input.endObject();
                } else {
                    input.skipValue();
                }
            }
            input.endObject();

            if (title == null) throw new EmbedNoTitleException(url);

            if (duration != null) {
                String urlString = url.toString();
                duration += urlString.contains("autoplay") ? "[AUTOPLAY]" : "";
                duration += urlString.contains("loop") ? "[LOOP]" : "";
            }

            return new EmbedResult(
                    title,
                    duration,
                    new PostImage.Builder()
                            .serverFilename(id)
                            .thumbnailUrl(HttpUrl.get(smallThumb))
                            .imageUrl(HttpUrl.get("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg"))
                            .filename("maxresdefault")
                            .extension("jpg")
                            .isInlined()
                            .build()
            );
        }).chain(input -> {
            Matcher paramsMatcher = API_PARAMS.matcher(response.body().string());
            if (paramsMatcher.find()) {
                return new JsonReader(new StringReader(paramsMatcher.group(1)));
            }
            return null;
        }).convert(response);
    }

    private String getLargestSmallThumb(JsonReader input)
            throws Exception {
        String biggestSmallThumb = null;
        int largestSoFar = 0;
        input.beginObject();
        while (input.hasNext()) {
            if ("thumbnails".equals(input.nextName())) {
                input.beginArray();
                while (input.hasNext()) {
                    input.beginObject();
                    input.nextName();
                    String url = input.nextString();
                    input.nextName();
                    int width = Integer.parseInt(input.nextString());
                    input.nextName();
                    int height = Integer.parseInt(input.nextString());
                    int pxSize = width * height;
                    if (pxSize > largestSoFar && !url.contains("maxres")) {
                        largestSoFar = pxSize;
                        biggestSmallThumb = url;
                    }
                    input.endObject();
                }
                input.endArray();
            } else {
                input.skipValue();
            }
        }
        input.endObject();
        return biggestSmallThumb;
    }
}
