package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;
import android.util.JsonToken;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class SoundcloudEmbedder
        implements Embedder {
    private static final Pattern SOUNDCLOUD_PATTERN =
            Pattern.compile("(https?://(?:\\w+\\.)?soundcloud\\.com/.*?/(?:sets/)?[A-Za-z0-9-_.!~*'()]*)(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "soundcloud");
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
        return HttpUrl.get("https://w.soundcloud.com/player/?visual=true&show_artwork=true&url=" + matcher.group(1));
    }

    @SuppressWarnings("RegExpRedundantEscape") // complains, but otherwise will fail at runtime
    private final Pattern JSON_PATTERN = Pattern.compile("var c=(\\[\\{.*\\}\\])");

    @Override
    public EmbedResult convert(Response response)
            throws Exception {
        // we're getting HTML back, but we need to process some JSON from within a script
        HttpUrl sourceURL = response.request().url();
        Document document = Jsoup.parse(response.body().byteStream(), null, sourceURL.toString());
        JsonReader reader = null;
        for (Element e : document.select("script")) {
            String innerHTML = e.html();
            if (innerHTML.startsWith("webpackJsonp")) {
                Matcher jsonMatcher = JSON_PATTERN.matcher(innerHTML);
                if (jsonMatcher.find()) {
                    reader = new JsonReader(new StringReader(jsonMatcher.group(1)));
                }
            }
        }

        // process the JSON
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
                (artist + title).isEmpty()
                        ? sourceURL.toString().substring(sourceURL.toString().lastIndexOf('=') + 1)
                        : title + " | " + artist,
                duration,
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(artworkURL == null ? HttpUrl.get(
                                BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png") : artworkURL)
                        .imageUrl(sourceURL)
                        .filename(title)
                        .extension("iframe")
                        .isInlined()
                        .build()
        );
    }
}
