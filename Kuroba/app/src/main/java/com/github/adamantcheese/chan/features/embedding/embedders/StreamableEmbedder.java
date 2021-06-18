package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.random.Random;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

public class StreamableEmbedder
        extends JsonEmbedder {
    private static final Pattern STREAMABLE_LINK_PATTERN =
            Pattern.compile("https?://(?:www\\.)?streamable\\.com/(.{6})(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "streamable");
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
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            String serverFilename = "";
            HttpUrl mp4Url = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
            HttpUrl thumbnailUrl = null;
            long size = -1L;

            String title = "titleMissing" + Random.Default.nextDouble();
            double duration = Double.NaN;

            input.beginObject(); // JSON start
            while (input.hasNext()) {
                String name = input.nextName();
                switch (name) {
                    case "url":
                        serverFilename = input.nextString();
                        serverFilename = serverFilename.substring(serverFilename.indexOf('/') + 1);
                        break;
                    case "files":
                        input.beginObject();
                        while (input.hasNext()) {
                            String format = input.nextName();
                            if ("mp4".equals(format)) {
                                input.beginObject();
                                while (input.hasNext()) {
                                    String innerName = input.nextName();
                                    switch (innerName) {
                                        case "duration":
                                            duration = input.nextDouble();
                                            break;
                                        case "url":
                                            mp4Url = HttpUrl.get(input.nextString());
                                            break;
                                        case "size":
                                            size = input.nextLong();
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
                        break;
                    case "title":
                        title = input.nextString();
                        break;
                    case "thumbnail_url":
                        thumbnailUrl = HttpUrl.get("https:" + input.nextString());
                        break;
                    default:
                        input.skipValue();
                        break;
                }
            }
            input.endObject();

            return new EmbedResult(
                    title,
                    prettyPrintDateUtilsElapsedTime(duration),
                    new PostImage.Builder().serverFilename(serverFilename)
                            .thumbnailUrl(thumbnailUrl)
                            .imageUrl(mp4Url)
                            .filename(title)
                            .extension("mp4")
                            .isInlined()
                            .size(size)
                            .build()
            );
        };
    }
}
