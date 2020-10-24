package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.JsonReader;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.NetUtils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

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
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;

public class QuickLatexEmbedder
        implements Embedder {
    private final Pattern MATH_EQN_PATTERN = Pattern.compile("\\[(?:math|eqn)].*?\\[/(?:math|eqn)]");
    private final Pattern QUICK_LATEX_RESPONSE =
            Pattern.compile(".*?\r\n(\\S+)\\s.*?\\s\\d+\\s\\d+(?:\r\n([\\s\\S]+))?");

    // maps a math string to a rendered image URL
    public static LruCache<String, HttpUrl> mathCache = new LruCache<>(100);

    @Override
    public List<String> getShortRepresentations() {
        return Collections.emptyList(); // this embedder doesn't prevent any URL autolinking
    }

    @Override
    public Bitmap getIconBitmap() {
        return null; // this embedder doesn't use a bitmap title
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return MATH_EQN_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return null; // this embedder is special, see setupMathImageUrlRequest below for this URL
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        List<Pair<Call, Callback>> ret = new ArrayList<>();
        Set<Pair<String, String>> toReplace = new HashSet<>();
        Matcher linkMatcher = getEmbedReplacePattern().matcher(post.comment);
        while (linkMatcher.find()) {
            final String rawMath = linkMatcher.group(0);
            if (rawMath == null) continue;

            String sanitizedMath = rawMath.replace("[math]", "$")
                    .replace("[eqn]", "$$")
                    .replace("[/math]", "$")
                    .replace("[/eqn]", "$$")
                    .replace("%", "%25")
                    .replace("&", "%26");
            toReplace.add(new Pair<>(rawMath, sanitizedMath));
        }

        for (Pair<String, String> math : toReplace) {
            HttpUrl imageUrl = mathCache.get(math.first);
            if (imageUrl != null) {
                // have a previous image URL
                ret.add(new Pair<>(new NetUtils.NullCall(imageUrl), new NetUtils.IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        Pair<Call, Callback> ret = generateMathSpanCalls(post, imageUrl, math.first);
                        if (ret == null || ret.first == null || ret.second == null) return;
                        try {
                            ret.second.onResponse(ret.first, ret.first.execute());
                        } catch (Exception ignored) {
                        }
                    }
                }));
            } else {
                // need to request an image URL
                ret.add(new Pair<>(
                        instance(NetModule.OkHttpClientWithUtils.class).newCall(setupMathImageUrlRequest(math.second)),
                        new NetUtils.IgnoreFailureCallback() {
                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) {
                                if (!response.isSuccessful()) {
                                    response.close();
                                    return;
                                }

                                try (ResponseBody body = response.body()) {
                                    //noinspection ConstantConditions
                                    String responseString = body.string();
                                    Matcher matcher = QUICK_LATEX_RESPONSE.matcher(responseString);
                                    if (matcher.matches()) {
                                        //noinspection ConstantConditions
                                        HttpUrl url = HttpUrl.get(matcher.group(1));
                                        String err = matcher.group(2);
                                        if (err == null) {
                                            mathCache.put(math.first, url);
                                            Pair<Call, Callback> ret = generateMathSpanCalls(post, url, math.first);
                                            if (ret == null || ret.first == null || ret.second == null) return;
                                            ret.second.onResponse(ret.first, ret.first.execute());
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                ));
            }
        }
        return ret;
    }

    @Override
    public EmbeddingEngine.EmbedResult parseResult(JsonReader jsonReader, Document htmlDocument) {
        return null; // not used, quicklatex doesn't return a standard JSON or HTML stream
    }

    private Request setupMathImageUrlRequest(String formula) {
        //@formatter:off
        String postBody =
                "formula=" + formula +
                "&fsize=" + (int) (sp(ChanSettings.fontSize.get()) * 1.2) + "px" +
                "&fcolor=" + getRGBColorIntString(getAttrColor(ThemeHelper.getTheme().resValue, android.R.attr.textColor)) +
                "&mode=0" +
                "&out=1" +
                "&preamble=\\usepackage{amsmath}\r\n\\usepackage{amsfonts}\r\n\\usepackage{amssymb}" +
                "&rnd=" + Math.random() * 100 +
                "&remhost=quicklatex.com";
        //@formatter:on
        return new Request.Builder().url("https://www.quicklatex.com/latex3.f")
                .post(RequestBody.create(postBody, null))
                .build();
    }

    private Pair<Call, Callback> generateMathSpanCalls(
            Post post, @NonNull final HttpUrl imageUrl, final String rawMath
    ) {
        // execute immediately, so that the invalidate function is called when all embeds are done
        // that means that we can't enqueue this request
        return NetUtils.makeBitmapRequest(imageUrl, new NetUtils.BitmapResult() {
            @Override
            public void onBitmapFailure(
                    Bitmap errormap, Exception e
            ) {} // don't do any replacements with failed bitmaps, leave it as-is so it's somewhat still readable

            @Override
            public void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache) {
                synchronized (post.comment) {
                    for (int i = 0; i < post.comment.length(); ) {
                        int startIndex = post.comment.toString().indexOf(rawMath, i);
                        int endIndex = startIndex + rawMath.length();

                        i = endIndex + 1;

                        if (startIndex == -1) {
                            return; // don't know where to do replacement or finished all replacements (in the case of multiple of the same latex)
                        }

                        if (post.comment.getSpans(startIndex, endIndex, ImageSpan.class).length > 0) {
                            continue; // we've already got an image span attached here
                        }

                        ImageSpan mathImage = new ImageSpan(getAppContext(), bitmap);
                        post.comment.setSpan(
                                mathImage,
                                startIndex,
                                endIndex,
                                ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                        | Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        );
                    }
                }
            }
        }, 0, 0, false);
    }
}
