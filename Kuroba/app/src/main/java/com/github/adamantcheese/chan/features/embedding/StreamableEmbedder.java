package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;

public class StreamableEmbedder
        extends JsonEmbedder {
    private static final Pattern STREAMABLE_LINK_PATTERN =
            Pattern.compile("https?://(?:www\\.)?streamable\\.com/(.{6})(?:/|\\b)");

    @Override
    public List<String> getShortRepresentations() {
        return Collections.singletonList("streamable");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.streamableIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return STREAMABLE_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://api.streamable.com/videos/" + matcher.group(1));
    }

    /* SAMPLE JSON FOR STREAMABLE; note that this API is not well documented
    {
       "status": 2,
       "percent": 100,
       "url": "streamable.com/uhoe7l",
       "embed_code": "<div style=\"width: 100%; height: 0p...</div>",
       "message": null, PRETTY MUCH ALWAYS NULL
       "files": {
          "mp4": {
              "status": 2,
              "url": "https://cdn-cf-east.streamable.com...",
              "framerate": 30,
              "height": 720,
              "width": 1280,
              "bitrate": 2067499,
              "size": 150190909,
              "duration": 581.147233
          },
          "original": {
              "framerate": 29.97002997002997,
              "bitrate": 2063218,
              "size": 149879643,
              "duration": 581.147233,
              "height": 720,
              "width": 1280
          }
       },
       "thumbnail_url": "//cdn-cf-east.streamable.com/image/uhoe7l.jpg",
       "title": "",
       "source": "https://www.youtube.com/watch?v=Unnvj58sP3I" MAY BE NULL
    }
     */

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addStandardEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult process(JsonReader response)
            throws IOException {
        String serverFilename = "";
        HttpUrl mp4Url = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
        HttpUrl thumbnailUrl = null;
        long size = -1L;

        String title = "titleMissing" + Math.random();
        double duration = Double.NaN;

        response.beginObject(); // JSON start
        while (response.hasNext()) {
            String name = response.nextName();
            switch (name) {
                case "url":
                    serverFilename = response.nextString();
                    serverFilename = serverFilename.substring(serverFilename.indexOf('/') + 1);
                    break;
                case "files":
                    response.beginObject();
                    while (response.hasNext()) {
                        String format = response.nextName();
                        if ("mp4".equals(format)) {
                            response.beginObject();
                            while (response.hasNext()) {
                                String innerName = response.nextName();
                                switch (innerName) {
                                    case "duration":
                                        duration = response.nextDouble();
                                        break;
                                    case "url":
                                        mp4Url = HttpUrl.get(response.nextString());
                                        break;
                                    case "size":
                                        size = response.nextLong();
                                        break;
                                    default:
                                        response.skipValue();
                                        break;
                                }
                            }
                            response.endObject();
                        } else {
                            response.skipValue();
                        }
                    }
                    response.endObject();
                    break;
                case "title":
                    title = response.nextString();
                    break;
                case "thumbnail_url":
                    thumbnailUrl = HttpUrl.get("https:" + response.nextString());
                    break;
                default:
                    response.skipValue();
                    break;
            }
        }
        response.endObject();

        return new EmbedResult(
                title,
                "[" + DateUtils.formatElapsedTime(Math.round(duration)) + "]",
                new PostImage.Builder().serverFilename(serverFilename)
                        .thumbnailUrl(thumbnailUrl)
                        .imageUrl(mp4Url)
                        .filename(title)
                        .extension("mp4")
                        .isInlined(true)
                        .size(size)
                        .build()
        );
    }
}
