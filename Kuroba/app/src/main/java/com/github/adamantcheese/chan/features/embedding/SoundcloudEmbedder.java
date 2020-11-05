package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addStandardEmbedCalls;

public class SoundcloudEmbedder
        implements Embedder<Pair<HttpUrl, JsonReader>> {
    private static final Pattern SOUNDCLOUD_PATTERN =
            Pattern.compile("(https?://(?:\\w+\\.)?soundcloud\\.com/.*?/(?:sets/)?[A-Za-z0-9-_.!~*'()]*)(?:/|\\b)");

    @Override
    public List<CharSequence> getShortRepresentations() {
        return Collections.singletonList("soundcloud");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.soundcloudIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return SOUNDCLOUD_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get(
                "https://w.soundcloud.com/player/?visual=true&url=" + matcher.group(1) + "&show_artwork=true");
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addStandardEmbedCalls(this, theme, post);
    }

    @SuppressWarnings("RegExpRedundantEscape") // complains, but otherwise will fail at runtime
    private final Pattern JSON_PATTERN = Pattern.compile("var c=(\\[\\{.*\\}\\])");

    @Override
    public Pair<HttpUrl, JsonReader> convert(HttpUrl baseURL, @Nullable ResponseBody body)
            throws Exception {
        // we're getting HTML back, but we need to process some JSON from within a script
        Document document = Jsoup.parse(body.byteStream(), null, baseURL.toString());
        for (Element e : document.select("script")) {
            String innerHTML = e.html();
            if (innerHTML.startsWith("webpackJsonp")) {
                Matcher jsonMatcher = JSON_PATTERN.matcher(innerHTML);
                if (jsonMatcher.find()) {
                    return new Pair<>(baseURL, new JsonReader(new StringReader(jsonMatcher.group(1))));
                }
            }
        }
        return null;
    }

    @Override
    public EmbedResult process(Pair<HttpUrl, JsonReader> response)
            throws Exception {
        HttpUrl sourceURL = response.first;
        JsonReader reader = response.second;
        String artist = "";
        String title = "";
        String duration = null;
        HttpUrl artworkURL = null;

        reader.beginArray();
        reader.beginObject();
        while (reader.hasNext()) {
            if ("data".equals(reader.nextName())) {
                reader.beginArray();
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "artwork_url":
                            if (reader.peek() == JsonToken.NULL) {
                                reader.skipValue(); // if missing, the user's avatar is used
                            } else {
                                artworkURL = HttpUrl.get(reader.nextString().replace("large", "t500x500"));
                            }
                            break;
                        case "duration":
                            duration = StringUtils.prettyPrintDateUtilsElapsedTime(reader.nextDouble() / 1000);
                            break;
                        case "title":
                            title = reader.nextString();
                            break;
                        case "user":
                            reader.beginObject();
                            while (reader.hasNext()) {
                                switch (reader.nextName()) {
                                    case "username":
                                        artist = reader.nextString();
                                        break;
                                    case "avatar_url":
                                        if (artworkURL == null) {
                                            artworkURL = HttpUrl.get(reader.nextString().replace("large", "t500x500"));
                                        } else {
                                            reader.nextString();
                                        }
                                        break;
                                    default:
                                        reader.skipValue();
                                        break;
                                }
                            }
                            reader.endObject();
                            break;
                        default:
                            reader.skipValue();
                            break;
                    }
                }
                reader.endObject();
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.endArray();

        return new EmbedResult(
                (artist + title).isEmpty() ? sourceURL.toString() : title + " | " + artist,
                duration,
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(artworkURL == null ? HttpUrl.get(
                                BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png") : artworkURL)
                        .imageUrl(sourceURL)
                        .filename(title)
                        .extension("iframe")
                        .isInlined(true)
                        .build()
        );
    }
}
