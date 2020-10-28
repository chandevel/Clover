package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addJSONEmbedCalls;

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
        return HttpUrl.get(
                "https://www.googleapis.com/youtube/v3/videos?part=snippet" + (ChanSettings.parseYoutubeDuration.get()
                        ? "%2CcontentDetails"
                        : "") + "&id=" + matcher.group(1) + "&fields=items%28id%2Csnippet%28title%29"
                        + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails%28duration%29" : "")
                        + "%29&key=" + ChanSettings.parseYoutubeAPIKey.get());
    }

    //for testing, using 4chanx's api key
    //normal https://www.googleapis.com/youtube/v3/videos?part=snippet&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE
    //duration https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE

    /* SAMPLE JSON FOR YOUTUBE WITH DURATION (skip the contentDetails stuff for without)
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
        return addJSONEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult process(JsonReader response)
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
        if (ChanSettings.parseYoutubeDuration.get()) {
            response.nextName(); // content details
            response.beginObject();
            response.nextName(); // duration
            duration = getHourMinSecondString(response.nextString());
            response.endObject();
        }
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
