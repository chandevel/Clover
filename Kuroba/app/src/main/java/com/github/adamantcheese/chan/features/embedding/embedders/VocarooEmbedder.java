package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.IgnoreFailureCallback;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.performStandardEmbedding;

public class VocarooEmbedder
        extends VoidEmbedder {
    private static final Pattern VOCAROO_LINK_PATTERN =
            Pattern.compile("https?://(?:(?:www\\.)?vocaroo\\.com|voca\\.ro)/(\\w{11,12})(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "vocaroo", "voca.ro");
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
    public List<Pair<Call, Callback>> generateCallPairs(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages
    ) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        if (ChanSettings.parsePostImageLinks.get()) {
            Matcher linkMatcher = getEmbedReplacePattern().matcher(commentCopy);
            while (linkMatcher.find()) {
                String URL = linkMatcher.group(0);
                if (URL == null) continue;
                String id = linkMatcher.group(1);

                calls.add(new Pair<>(new NetUtilsClasses.NullCall(HttpUrl.get(URL)), new IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        performStandardEmbedding(theme,
                                commentCopy,
                                generatedLinkables,
                                generatedImages,
                                new EmbedResult("Vocaroo attached! ♫",
                                        "",
                                        new PostImage.Builder().serverFilename(id)
                                                .thumbnailUrl(HttpUrl.parse(
                                                        "https://vocarooblog.files.wordpress.com/2020/04/robotchibi-cropped-1.png"))
                                                .imageUrl(HttpUrl.get("https://media1.vocaroo.com/mp3/" + id))
                                                .filename("Vocaroo " + id)
                                                .extension("mp3")
                                                .isInlined()
                                                .build()
                                ),
                                URL,
                                getIconBitmap()
                        );
                    }
                }));
            }
        }

        return calls;
    }
}
