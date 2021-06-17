package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.StringReader;
import java.net.URLDecoder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;
import static com.github.adamantcheese.chan.utils.StringUtils.prettyPrintDateUtilsElapsedTime;

public class YoutubeEmbedder
        implements Embedder {
    // Group 1 is the video id, Group 2 is any parameters after the ID
    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "https?://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})([^\\s]*)(?:/|\\b)");
    // All the relevant information is hidden away in a var called ytInitialPlayerResponse; we can snag that JSON and use it
    private static final Pattern API_PARAMS = Pattern.compile("ytInitialPlayerResponse = (.*?);var");

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
            String url = response.request().url().toString();
            String title = "Title missing";
            String duration = "[?:??]";
            input.beginObject();
            while (input.hasNext()) {
                if ("videoDetails".equals(input.nextName())) {
                    input.beginObject();
                    while (input.hasNext()) {
                        switch (input.nextName()) {
                            case "title":
                                title = URLDecoder.decode(input.nextString(), "utf-8");
                                break;
                            case "lengthSeconds":
                                duration = prettyPrintDateUtilsElapsedTime(input.nextInt());
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
                } else {
                    input.skipValue();
                }
            }
            input.endObject();

            duration += url.contains("autoplay") ? "[AUTOPLAY]" : "";
            duration += url.contains("loop") ? "[LOOP]" : "";

            return new EmbedResult(title, duration, null);
        }).chain(input -> {
            Matcher paramsMatcher = API_PARAMS.matcher(response.body().string());
            if (paramsMatcher.find()) {
                return new JsonReader(new StringReader(paramsMatcher.group(1)));
            }
            return null;
        }).convert(response);
    }
}
