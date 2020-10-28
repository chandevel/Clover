package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.format.DateUtils;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.generateReplacements;
import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.getStandardCachedCallPair;
import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.getStandardResponseResult;
import static java.nio.charset.StandardCharsets.UTF_8;

public class YoutubeEmbedder
        implements Embedder<JsonReader> {
    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "https?://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})([^\\s]*)(?:/|\\b)");

    private static final Pattern iso8601Time = Pattern.compile("PT((\\d+)H)?((\\d+)M)?((\\d+)S)?");

    @Override
    public List<String> getShortRepresentations() {
        return Arrays.asList("youtu.be", "youtube");
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
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        if (!StringUtils.containsAny(post.comment.toString(), getShortRepresentations()))
            return Collections.emptyList();

        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(this, post);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = EmbeddingEngine.videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme, post, result, urlPair.first, getIconBitmap()));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeRequest(urlPair.second, body -> {
                    if (ChanSettings.parseYoutubeAPIKey.get().isEmpty()) {
                        Pattern params = Pattern.compile("player_response=(.*?)&");
                        Matcher paramsMatcher = params.matcher(URLDecoder.decode(body.string(), "utf-8"));
                        if (paramsMatcher.find()) {
                            return new JsonReader(new StringReader(paramsMatcher.group(1)));
                        }
                        return null;
                    } else {
                        return new JsonReader(new InputStreamReader(body.byteStream(), UTF_8));
                    }
                }, new NetUtilsClasses.JSONProcessor<EmbedResult>() {
                    @Override
                    public EmbedResult process(JsonReader response)
                            throws Exception {
                        return YoutubeEmbedder.this.process(response);
                    }
                }, getStandardResponseResult(theme, post, urlPair.first, getIconBitmap()), 2500, false, null));
            }
        }
        return calls;
    }

    @Override
    public EmbedResult process(JsonReader response)
            throws IOException {
        if (ChanSettings.parseYoutubeAPIKey.get().isEmpty()) {
            return processNoApiKey(response);
        } else {
            return processApiKey(response);
        }
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
                            duration = "[" + DateUtils.formatElapsedTime(response.nextInt()) + "]";
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
        String duration = null;
        response.endObject();
        response.nextName(); // content details
        response.beginObject();
        response.nextName(); // duration
        duration = getHourMinSecondString(response.nextString());
        response.endObject();
        response.endObject();
        response.endArray();
        response.endObject();
        return new EmbedResult(title, duration, null);
    }

    private static String getHourMinSecondString(String ISO8601Duration) {
        Matcher m = iso8601Time.matcher(ISO8601Duration);
        String ret;
        if (m.matches()) {
            String hours = m.group(2);
            String minutes = m.group(4);
            String seconds = m.group(6);
            //pad seconds to 2 digits
            seconds = seconds != null ? (seconds.length() == 1 ? "0" + seconds : seconds) : "00";
            if (hours != null) {
                //we have hours, pad minutes to 2 digits
                minutes = minutes != null ? (minutes.length() == 1 ? "0" + minutes : minutes) : null;
                ret = hours + ":" + (minutes != null ? minutes : "00") + ":" + seconds;
            } else {
                //no hours, no need to pad anything else
                ret = (minutes != null ? minutes : "00") + ":" + seconds;
            }
        } else if ("P0D".equals(ISO8601Duration)) {
            ret = "LIVE";
        } else {
            //badly formatted time from youtube's API?
            ret = "??:??";
        }

        return "[" + ret + "]";
    }
}
