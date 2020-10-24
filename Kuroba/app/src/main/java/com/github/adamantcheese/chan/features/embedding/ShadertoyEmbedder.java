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

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

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
import okhttp3.Response;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.performStandardEmbedding;

public class ShadertoyEmbedder
        implements Embedder {
    private static final Pattern SHADERTOY_PATTERN =
            Pattern.compile("https?://(?:www\\.)shadertoy\\.com/view/(.{6})\\b");

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
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        //find and replace all video URLs with their titles, but keep track in the map above for spans later
        Matcher linkMatcher = getEmbedReplacePattern().matcher(post.comment);
        Set<Pair<String, String>> toReplace = new HashSet<>();
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            String shaderID = linkMatcher.group(1);
            toReplace.add(new Pair<>(URL, shaderID));
        }

        for (Pair<String, String> infoPair : toReplace) {
            EmbedResult result = EmbeddingEngine.videoTitleDurCache.get(infoPair.first);

            if (result != null) {
                // we've previously cached this title/duration and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(new Pair<>(new NetUtils.NullCall(HttpUrl.get(infoPair.first)),
                        new NetUtils.IgnoreFailureCallback() {
                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) {
                                performStandardEmbedding(theme, post, result, infoPair.first, getIconBitmap());
                            }
                        }
                ));
            } else {
                // we haven't cached this media title/duration, or we need additional information
                calls.add(NetUtils.makePostJsonCall(generateRequestURL(linkMatcher),
                        new NetUtils.JsonResult<EmbedResult>() {
                            @Override
                            public void onJsonFailure(Exception e) {
                                if (!"Canceled".equals(e.getMessage())) {
                                    //failed to get, replace with just the URL and append the icon
                                    performStandardEmbedding(theme,
                                            post,
                                            new EmbedResult(infoPair.first, "", null),
                                            infoPair.first,
                                            getIconBitmap()
                                    );
                                }
                            }

                            @Override
                            public void onJsonSuccess(EmbedResult result) {
                                //got a result, replace with the result and also cache the result
                                EmbeddingEngine.videoTitleDurCache.put(infoPair.first, result);
                                performStandardEmbedding(theme, post, result, infoPair.first, getIconBitmap());
                            }
                        },
                        // bit of a hack to get the shaderID in
                        (reader -> parseResult(reader, new Document(infoPair.second))),
                        2500,
                        "s=" + Uri.encode("{ \"shaders\" : [\"" + infoPair.second + "\"] }") + "&nt=1&nl=1",
                        "application/x-www-form-urlencoded"
                ));
            }
        }
        return calls;
    }

    @Override
    public EmbedResult parseResult(JsonReader jsonReader, Document htmlDocument)
            throws IOException {
        String shaderID = htmlDocument.location();
        String title = shaderID;
        jsonReader.beginArray();
        jsonReader.beginObject();
        while (jsonReader.hasNext()) {
            if ("info".equals(jsonReader.nextName())) {
                jsonReader.beginObject(); // info object
                while (jsonReader.hasNext()) {
                    if ("name".equals(jsonReader.nextName())) {
                        title = jsonReader.nextString();
                    } else {
                        jsonReader.skipValue();
                    }
                }
                jsonReader.endObject();
            } else {
                jsonReader.skipValue();
            }
        }
        jsonReader.endObject();
        jsonReader.endArray();

        return new EmbedResult("Shadertoy - " + title,
                "",
                new PostImage.Builder().serverFilename(title)
                        .thumbnailUrl(HttpUrl.get("https://www.shadertoy.com/media/shaders/" + shaderID + ".jpg"))
                        .imageUrl(HttpUrl.get("https://www.shadertoy.com/embed/" + shaderID
                                + "?gui=true&t=10&paused=false&muted=true"))
                        .filename(title)
                        .extension("iframe")
                        .isInlined(true)
                        .imageWidth(MATCH_PARENT) // this iframe should fill the available view space
                        .imageHeight(MATCH_PARENT) // this iframe should fill the available view space
                        .build()
        );
    }
}
