package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrint8601Time;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;
import static java.nio.charset.StandardCharsets.UTF_8;

public class YoutubeEmbedder
        implements Embedder {
    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "https?://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})([^\\s]*)(?:/|\\b)");
    private static final Pattern API_PARAMS = Pattern.compile("player_response=(.*?)&");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "youtu.be", "youtube");
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
        if (ChanSettings.parseYoutubeAPIKey.get().isEmpty()) {
            return HttpUrl.get("https://www.youtube.com/get_video_info?el=detailpage&video_id=" + matcher.group(1));
        } else {
            return HttpUrl.get(
                    "https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=" + matcher.group(1)
                            + "&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key="
                            + ChanSettings.parseYoutubeAPIKey.get());
        }
    }

    //for testing, using 4chanx's api key
    //normal https://www.googleapis.com/youtube/v3/videos?part=snippet&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE
    //duration https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE

    /* SAMPLE JSON FOR YOUTUBE WITH DURATION AND API KEY
        {
          "items": [
            {
              "id": "UyXlt9PP4eM",
              "snippet": {
                "title": "ATC Spindle Part 3: Designing the Spindle Mount"
              },
              "contentDetails": {
                "duration": "PT22M27S"
              }
            }
          ]
        }
     */

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages
    ) {
        return addStandardEmbedCalls(this, theme, commentCopy, generatedLinkables, generatedImages);
    }

    @Override
    public EmbedResult convert(Response response)
            throws Exception {
        return new NetUtilsClasses.ChainConverter<EmbedResult, JsonReader>(input -> {
            if (ChanSettings.parseYoutubeAPIKey.get().isEmpty()) {
                return processNoApiKey(input);
            } else {
                return processApiKey(input);
            }
        }).chain(input -> {
            if (ChanSettings.parseYoutubeAPIKey.get().isEmpty()) {
                Matcher paramsMatcher = API_PARAMS.matcher(URLDecoder.decode(response.body().string(), "utf-8"));
                if (paramsMatcher.find()) {
                    return new JsonReader(new StringReader(paramsMatcher.group(1)));
                }
                return null;
            } else {
                return new JsonReader(new InputStreamReader(response.body().byteStream(), UTF_8));
            }
        }).convert(response);
    }

    private static EmbedResult processNoApiKey(JsonReader response)
            throws IOException {
        String title = "Title missing";
        String duration = "[?:??]";
        response.beginObject();
        while (response.hasNext()) {
            if ("videoDetails".equals(response.nextName())) {
                response.beginObject();
                while (response.hasNext()) {
                    switch (response.nextName()) {
                        case "title":
                            title = URLDecoder.decode(response.nextString(), "utf-8");
                            break;
                        case "lengthSeconds":
                            duration = prettyPrintDateUtilsElapsedTime(response.nextInt());
                            break;
                        case "isLiveContent":
                            if (response.nextBoolean()) {
                                duration = "[LIVE]";
                            }
                            break;
                        default:
                            response.skipValue();
                            break;
                    }
                }
            } else {
                response.skipValue();
            }
        }
        response.endObject();

        return new EmbedResult(title, duration, null);
    }

    private static EmbedResult processApiKey(JsonReader response)
            throws IOException {
        response.beginObject(); // JSON start
        response.nextName();
        response.beginArray();
        response.beginObject();
        response.nextName(); // video ID
        response.nextString();
        response.nextName(); // snippet
        response.beginObject();
        response.nextName(); // title
        String title = response.nextString();
        response.endObject();
        response.nextName(); // content details
        response.beginObject();
        response.nextName(); // duration
        String duration = prettyPrint8601Time(response.nextString());
        response.endObject();
        response.endObject();
        response.endArray();
        response.endObject();
        return new EmbedResult(title, duration, null);
    }
}
