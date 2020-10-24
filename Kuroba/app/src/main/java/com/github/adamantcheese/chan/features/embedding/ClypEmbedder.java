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

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addJSONEmbedCalls;

public class ClypEmbedder
        implements Embedder {
    private static final Pattern CLYP_LINK_PATTERN = Pattern.compile("https?://clyp.it/(\\w{8})");

    @Override
    public List<String> getShortRepresentations() {
        return Collections.singletonList("clyp.it");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.clypIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return CLYP_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://api.clyp.it/" + matcher.group(1));
    }

    /* EXAMPLE JSON
    {
      "Status": "DownloadDisabled",
      "CommentsEnabled": true,
      "Category": "None",
      "AudioFileId": "j42441xr",
      "Title": "ob6 + piano + bigsky",
      "Description": "first encounter with the big sky",
      "Duration": 67.709,
      "Url": "https://clyp.it/j42441xr",
      "Mp3Url": "https://audio.clyp.it/j42441xr.mp3?Exp...",
      "SecureMp3Url": "https://audio.clyp.it/j42441xr.mp3?Exp...",
      "OggUrl": "https://audio.clyp.it/j42441xr.ogg?Exp...",
      "SecureOggUrl": "https://audio.clyp.it/j42441xr.ogg?Exp...",
      "DateCreated": "2020-09-20T05:16:29.473Z"
    }
     */

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addJSONEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult parseResult(JsonReader jsonReader, Document htmlDocument)
            throws IOException {
        String title = "titleMissing" + Math.random();
        double duration = Double.NaN;

        HttpUrl mp3Url = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "audio_thumb.png");
        String fileId = "";

        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            String name = jsonReader.nextName();
            switch (name) {
                case "Title":
                    title = jsonReader.nextString();
                    break;
                case "Duration":
                    duration = jsonReader.nextDouble();
                    break;
                case "AudioFileId":
                    fileId = jsonReader.nextString();
                    break;
                case "Mp3Url":
                    mp3Url = HttpUrl.get(jsonReader.nextString());
                    break;
                default:
                    jsonReader.skipValue();
                    break;
            }
        }
        jsonReader.endObject();

        return new EmbedResult(
                title,
                "[" + DateUtils.formatElapsedTime(Math.round(duration)) + "]",
                new PostImage.Builder().serverFilename(fileId)
                        .thumbnailUrl(HttpUrl.get(
                                "https://static.clyp.it/site/images/favicons/apple-touch-icon-precomposed.png"))
                        .imageUrl(mp3Url)
                        .filename(title)
                        .extension("mp3")
                        .isInlined(true)
                        .build()
        );
    }
}
