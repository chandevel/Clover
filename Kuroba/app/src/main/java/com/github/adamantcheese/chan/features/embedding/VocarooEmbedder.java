package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtils;

import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.performStandardEmbedding;

public class VocarooEmbedder
        implements Embedder {
    private static final Pattern VOCAROO_LINK_PATTERN =
            Pattern.compile("https?://(?:(?:www\\.)?vocaroo\\.com|voca\\.ro)/(\\w{12})\\b");

    @Override
    public List<String> getShortRepresentations() {
        return Arrays.asList("vocaroo", "voca.ro");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.vocarooIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return VOCAROO_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return null; // unused in this class
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        if (ChanSettings.parsePostImageLinks.get()) {
            Matcher linkMatcher = getEmbedReplacePattern().matcher(post.comment);
            while (linkMatcher.find()) {
                final String URL = linkMatcher.group(0);
                if (URL == null) continue;
                final String id = linkMatcher.group(1);

                calls.add(new Pair<>(new NetUtils.NullCall(HttpUrl.get(URL)), new NetUtils.IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        performStandardEmbedding(theme, post, new EmbedResult(
                                "Vocaroo attached! ♫",
                                "",
                                new PostImage.Builder().serverFilename(id)
                                        .thumbnailUrl(HttpUrl.parse(
                                                "https://vocarooblog.files.wordpress.com/2020/04/robotchibi-cropped-1.png"))
                                        .imageUrl(HttpUrl.get("https://media1.vocaroo.com/mp3/" + id))
                                        .filename("Vocaroo " + id)
                                        .extension("mp3")
                                        .isInlined(true)
                                        .build()
                        ), URL, getIconBitmap());
                    }
                }));
            }
        }

        return calls;
    }

    @Override
    public EmbedResult parseResult(JsonReader jsonReader, Document htmlDocument)
            throws IOException {
        return null; // not used for this embedder, as everything's in the URL
    }
}
