package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
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
        implements Embedder<String> {
    private final Pattern MATH_EQN_PATTERN = Pattern.compile("\\[(?:math|eqn)].*?\\[/(?:math|eqn)]");
    private final Pattern QUICK_LATEX_RESPONSE =
            Pattern.compile(".*?\r\n(\\S+)\\s.*?\\s\\d+\\s\\d+(?:\r\n([\\s\\S]+))?");

    // maps a math string to a rendered image URL
    public static LruCache<String, HttpUrl> mathCache = new LruCache<>(100);

    @Override
    public List<CharSequence> getShortRepresentations() {
        // this embedder doesn't prevent any URL autolinking, but has quick checking to skip expensive operations
        return Arrays.asList("[math]", "[eqn]");
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
        if (!post.board.mathTags) return ret; // only parse math on math enabled boards

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
                ret.add(new Pair<>(new NetUtilsClasses.NullCall(imageUrl), new NetUtilsClasses.IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        BackgroundUtils.runOnBackgroundThread(() -> {
                            Pair<Call, Callback> ret = generateMathSpanCalls(post, imageUrl, math.first);
                            if (ret == null || ret.first == null || ret.second == null) return;
                            try {
                                ret.second.onResponse(ret.first, ret.first.execute());
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }));
            } else {
                // need to request an image URL
                ret.add(new Pair<>(
                        instance(NetModule.OkHttpClientWithUtils.class).newCall(setupMathImageUrlRequest(math.second)),
                        new NetUtilsClasses.IgnoreFailureCallback() {
                            @Override
                            public void onResponse(@NotNull Call call, @NotNull Response response) {
                                if (!response.isSuccessful()) {
                                    response.close();
                                    return;
                                }

                                try {
                                    String responseString = convert(null, response.body());
                                    Matcher matcher = QUICK_LATEX_RESPONSE.matcher(responseString);
                                    if (matcher.matches()) {
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
                                } finally {
                                    response.close();
                                }
                            }
                        }
                ));
            }
        }
        return ret;
    }

    @Override
    public String convert(HttpUrl baseURL, @Nullable ResponseBody body)
            throws Exception {
        return body == null ? null : body.string();
    }

    @Override
    public EmbeddingEngine.EmbedResult process(String response) {
        return null; // not used, this embedder is rather specialized
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
        return NetUtils.makeBitmapRequest(imageUrl, new NetUtilsClasses.BitmapResult() {
            @Override
            public void onBitmapFailure(HttpUrl source, Exception e) {} // don't do any replacements with failed bitmaps, leave it as-is so it's somewhat still readable

            @Override
            public void onBitmapSuccess(HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                int startIndex = 0;
                while (true) {
                    synchronized (post.comment) {
                        startIndex = TextUtils.indexOf(post.comment, rawMath, startIndex);
                        if (startIndex < 0) break;

                        SpannableStringBuilder replacement = new SpannableStringBuilder(" ");
                        replacement.setSpan(
                                new ImageSpan(getAppContext(), bitmap),
                                0,
                                1,
                                ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                        | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                        );

                        // replace the proper section of the comment
                        post.comment.replace(startIndex, startIndex + rawMath.length(), replacement);

                        // update the index to the next location
                        startIndex = startIndex + replacement.length();
                    }
                }
            }
        }, 0, 0, false);
    }
}
