package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.JSONProcessor;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.getStandardCachedCallPair;
import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.getStandardResponseResult;

public class ShadertoyEmbedder
        implements Embedder<Pair<String, JsonReader>> {
    private static final Pattern SHADERTOY_PATTERN =
            Pattern.compile("https?://(?:www\\.)shadertoy\\.com/view/(.{6})(?:/|\\b)");

    @Override
    public List<String> getShortRepresentations() {
        return Collections.singletonList("shadertoy");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.shadertoyIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return SHADERTOY_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://www.shadertoy.com/shadertoy");
    }

    // Note this is a custom implementation of addJSONEmbedCalls
    // A LOT of this is taken from Shadertoy's website by sifting through their Javascript files, so I can't put an example like the others
    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        if (!StringUtils.containsAny(post.comment.toString(), getShortRepresentations()))
            return Collections.emptyList();

        List<Pair<Call, Callback>> calls = new ArrayList<>();
        //find and replace all video URLs with their titles, but keep track in the map above for spans later
        Matcher linkMatcher = getEmbedReplacePattern().matcher(post.comment);
        Set<Pair<String, String>> toReplace = new HashSet<>();
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            String shaderID = linkMatcher.group(1);
            // nonstandard, we need the shader ID and don't care for a generated URL as it is always the same
            toReplace.add(new Pair<>(URL, shaderID));
        }

        for (Pair<String, String> infoPair : toReplace) {
            EmbedResult result = EmbeddingEngine.videoTitleDurCache.get(infoPair.first);

            if (result != null) {
                // we've previously cached this title/duration and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme, post, result, infoPair.first, getIconBitmap()));
            } else {
                // we haven't cached this media title/duration, or we need additional information
                calls.add(NetUtils.makePostJsonCall(
                        generateRequestURL(linkMatcher),
                        getStandardResponseResult(theme, post, infoPair.first, getIconBitmap()),
                        new JSONProcessor<EmbedResult>() {
                            @Override
                            public EmbedResult process(JsonReader response)
                                    throws Exception { // nonstandard, requires the shader ID to be passed in
                                return ShadertoyEmbedder.this.process(new Pair<>(infoPair.second, response));
                            }
                        },
                        2500,
                        // nonstandard, shadertoy needs a POST request with some extra data
                        "s=" + Uri.encode("{ \"shaders\" : [\"" + infoPair.second + "\"] }") + "&nt=1&nl=1",
                        "application/x-www-form-urlencoded"
                ));
            }
        }
        return calls;
    }

    @Override
    public EmbedResult process(Pair<String, JsonReader> response)
            throws IOException {
        String shaderID = response.first;
        JsonReader reader = response.second;
        String title = shaderID;

        reader.beginArray();
        reader.beginObject();
        while (reader.hasNext()) {
            if ("info".equals(reader.nextName())) {
                reader.beginObject(); // info object
                while (reader.hasNext()) {
                    if ("name".equals(reader.nextName())) {
                        title = reader.nextString();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        reader.endArray();

        return new EmbedResult(
                "Shadertoy - " + title,
                "",
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(HttpUrl.get("https://www.shadertoy.com/media/shaders/" + shaderID + ".jpg"))
                        .imageUrl(HttpUrl.get("https://www.shadertoy.com/embed/" + shaderID
                                + "?gui=true&t=10&paused=false&muted=true"))
                        .filename(title)
                        .extension("iframe")
                        .isInlined(true)
                        .build()
        );
    }
}
