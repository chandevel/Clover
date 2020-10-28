package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.JsonReader;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.HTMLProcessor;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.IgnoreFailureCallback;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.JSONProcessor;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.NullCall;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class EmbeddingEngine {
    public final List<Embedder<?>> embedders = new ArrayList<>();

    public EmbeddingEngine() {
        // Media embedders
        embedders.add(new YoutubeEmbedder());
        embedders.add(new StreamableEmbedder());
        embedders.add(new VocarooEmbedder());
        embedders.add(new ClypEmbedder());
        embedders.add(new SoundcloudEmbedder());
        embedders.add(new BandcampEmbedder());
        embedders.add(new ShadertoyEmbedder());
        embedders.add(new VimeoEmbedder());

        // Special embedders
        embedders.add(new QuickLatexEmbedder());
    }

    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, EmbedResult> videoTitleDurCache = new LruCache<>(500);

    //region Image Inlining
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

    public static void addPostImages(Post.Builder post) {
        if (ChanSettings.parsePostImageLinks.get()) {
            for (PostLinkable linkable : post.getLinkables()) {
                if (post.images != null && post.images.size() >= 5) return; //max 5 images hotlinked this way
                if (linkable.type == PostLinkable.Type.LINK) {
                    Matcher matcher = IMAGE_URL_PATTERN.matcher((String) linkable.value);
                    if (matcher.matches()) {
                        boolean noThumbnail = StringUtils.endsWithAny((String) linkable.value, noThumbLinkSuffixes);
                        String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";

                        HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);
                        if (imageUrl == null
                                || ((String) linkable.value).contains("saucenao")) { // ignore saucenao links, not actual images
                            continue;
                        }

                        PostImage inlinedImage = new PostImage.Builder().serverFilename(matcher.group(1))
                                //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                                .thumbnailUrl(HttpUrl.parse(noThumbnail ? spoilerThumbnail : (String) linkable.value))
                                .spoilerThumbnailUrl(HttpUrl.parse(spoilerThumbnail))
                                .imageUrl(imageUrl)
                                .filename(matcher.group(1))
                                .extension(matcher.group(2))
                                .spoiler(true)
                                .isInlined(true)
                                .size(-1)
                                .build();

                        post.images(Collections.singletonList(inlinedImage));

                        NetUtils.makeHeadersRequest(imageUrl, new ResponseResult<Headers>() {
                            @Override
                            public void onFailure(Exception e) {}

                            @Override
                            public void onSuccess(Headers result) {
                                String size = result.get("Content-Length");
                                inlinedImage.size = size == null ? 0 : Long.parseLong(size);
                            }
                        });
                    }
                }
            }
        }
    }
    //endregion

    /**
     * To add a media link parser:<br>
     * 1) Implement the Embedder class.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- The helper methods addHTML/JSONEmbedCalls should be used for most stuff, it takes in all the appropriate stuff you'll need to implement functionality<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- For more complicated stuff, you can extend the necessary items yourself quite easily.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- Look at already implemented embedders for hints! Youtube's is probably the easiest to understand for a baseline.
     * 2) Add it to the EmbeddingEngine constructor.
     * 3) Done! Everything else is taken care of for you.<br>
     * <br>
     *
     * @param theme              The theme to style the links with
     * @param post               The post where the links will be found and replaced
     * @param invalidateFunction The entire view to be refreshed after embedding
     * @return A list of enqueued calls, if any
     */

    public List<Call> embed(Theme theme, @NonNull Post post, @NonNull InvalidateFunction invalidateFunction) {
        if (!post.needsEmbedding) return Collections.emptyList(); // these calls are processing/finished/not needed
        // Generate all the calls
        final List<Pair<Call, Callback>> generatedCallPairs = new ArrayList<>();
        for (Embedder<?> e : embedders) {
            generatedCallPairs.addAll(e.generateCallPairs(theme, post));
        }

        if (generatedCallPairs.isEmpty()) { // nothing to embed
            BackgroundUtils.runOnMainThread(() -> invalidateFunction.invalidateView(true));
            return Collections.emptyList();
        }

        // Set up and enqueue all the generated calls
        final int callCount = generatedCallPairs.size();
        final AtomicInteger processed = new AtomicInteger();
        final List<Call> enqueuedCalls = new ArrayList<>();
        for (Pair<Call, Callback> c : generatedCallPairs) {
            // enqueue all at the same time, wrapped callback to check when everything's complete
            c.first.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    if (!"Canceled".equals(e.getMessage())) {
                        c.second.onFailure(call, e);
                        checkInvalidate();
                    }
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response)
                        throws IOException {
                    c.second.onResponse(call, response);
                    checkInvalidate();
                }

                private void checkInvalidate() {
                    if (callCount != processed.incrementAndGet()) return; // still completing calls
                    // only invalidate ONCE, after all processing is done for all things
                    // prevents multiple view refreshes that may cause layout issues
                    BackgroundUtils.runOnMainThread(() -> invalidateFunction.invalidateView(false));
                    post.needsEmbedding = false;
                }
            });
            enqueuedCalls.add(c.first);
        }
        return enqueuedCalls;
    }

    //region Embedding Helper Functions
    @SuppressWarnings("ConstantConditions")
    public static List<Pair<Call, Callback>> addJSONEmbedCalls(Embedder<JsonReader> embedder, Theme theme, Post post) {
        if (!StringUtils.containsAny(post.comment.toString(), embedder.getShortRepresentations()))
            return Collections.emptyList();

        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, post);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme, post, result, urlPair.first, embedder.getIconBitmap()));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeJsonCall(urlPair.second,
                        getStandardResponseResult(theme, post, urlPair.first, embedder.getIconBitmap()),
                        new JSONProcessor<EmbedResult>() {
                            @Override
                            public EmbedResult process(JsonReader response)
                                    throws Exception {
                                return embedder.process(response);
                            }
                        },
                        2500
                ));
            }
        }
        return calls;
    }

    @SuppressWarnings("ConstantConditions")
    public static List<Pair<Call, Callback>> addHTMLEmbedCalls(Embedder<Document> embedder, Theme theme, Post post) {
        if (!StringUtils.containsAny(post.comment.toString(), embedder.getShortRepresentations()))
            return Collections.emptyList();

        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, post);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme, post, result, urlPair.first, embedder.getIconBitmap()));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeHTMLCall(urlPair.second,
                        getStandardResponseResult(theme, post, urlPair.first, embedder.getIconBitmap()),
                        new HTMLProcessor<EmbedResult>() {
                            @Override
                            public EmbedResult process(Document response)
                                    throws Exception {
                                return embedder.process(response);
                            }
                        },
                        2500
                ));
            }
        }
        return calls;
    }

    public static Set<Pair<String, HttpUrl>> generateReplacements(Embedder<?> embedder, Post post) {
        Set<Pair<String, HttpUrl>> result = new HashSet<>();
        Matcher linkMatcher = embedder.getEmbedReplacePattern().matcher(post.comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            result.add(new Pair<>(URL, embedder.generateRequestURL(linkMatcher)));
        }
        return result;
    }

    public static ResponseResult<EmbedResult> getStandardResponseResult(
            Theme theme, Post post, String URL, Bitmap iconBitmap
    ) {
        return new ResponseResult<EmbedResult>() {
            @Override
            public void onFailure(Exception e) {
                if (!"Canceled".equals(e.getMessage())) {
                    //failed to get, replace with just the URL and append the icon
                    performStandardEmbedding(theme, post, new EmbedResult(URL, null, null), URL, iconBitmap);
                }
            }

            @Override
            public void onSuccess(EmbedResult result) {
                //got a result, replace with the result and also cache the result
                videoTitleDurCache.put(URL, result);
                performStandardEmbedding(theme, post, result, URL, iconBitmap);
            }
        };
    }

    public static Pair<Call, Callback> getStandardCachedCallPair(
            Theme theme, Post post, EmbedResult result, String URL, Bitmap iconBitmap
    ) {
        return new Pair<>(new NullCall(HttpUrl.get(URL)), new IgnoreFailureCallback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                BackgroundUtils.runOnBackgroundThread(() -> performStandardEmbedding(theme,
                        post,
                        result,
                        URL,
                        iconBitmap
                ));
            }
        });
    }

    /**
     * Performs a "standard" embed of an icon followed by a title and optional duration.<br>
     * Additionally, this method adds in any embed post image results to the given post.<br>
     * <br>
     * Generally don't use this directly, use it through one of the addHTML/JSONEmbedCalls methods
     */
    public static void performStandardEmbedding(
            Theme theme, Post post, @NonNull EmbedResult parseResult, @NonNull String URL, Bitmap icon
    ) {

        int index = 0;
        while (true) { // this will always break eventually
            synchronized (post.comment) {
                // search from the last known replacement location
                index = TextUtils.indexOf(post.comment, URL, index);
                if (index < 0) break;

                // Generate a fresh replacement string (in case of repeats)
                SpannableStringBuilder replacement = new SpannableStringBuilder(
                        "  " + parseResult.title + (!TextUtils.isEmpty(parseResult.duration) ? " "
                                + parseResult.duration : ""));

                // Set the icon span for the linkable
                ImageSpan siteIcon = new ImageSpan(getAppContext(), icon);
                int height = sp(ChanSettings.fontSize.get());
                int width = (int) (height / (icon.getHeight() / (float) icon.getWidth()));
                siteIcon.getDrawable().setBounds(0, 0, width, height);
                replacement.setSpan(siteIcon,
                        0,
                        1,
                        ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                );

                // Set the linkable to be the entire length, including the icon
                PostLinkable pl = new PostLinkable(theme, replacement, URL, PostLinkable.Type.LINK);
                replacement.setSpan(pl,
                        0,
                        replacement.length(),
                        ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                );

                // replace the proper section of the comment with the link
                post.comment.replace(index, index + URL.length(), replacement);
                post.linkables.add(pl);

                // if linking is enabled, add in any processed inlines
                if (ChanSettings.parsePostImageLinks.get() && parseResult.extraImage != null) {
                    post.addImage(parseResult.extraImage);
                }
                // update the index to the next location
                index = index + replacement.length();
            }
        }
    }

    public static class EmbedResult {
        /**
         * The title for this embed result. While this can't be null, also don't make this an empty string either.
         */
        public String title;

        /**
         * An optional duration for this embed. If null and duration parsing is enabled, then another request will be sent to get a duration.
         * If you don't want/have any duration data, set this to an empty string.
         */
        public String duration;

        /**
         * An optional post image that is generated by an embedder, if image hotlinking is enabled. Makes some embeds more easily viewable in-app quickly.
         */
        public PostImage extraImage;

        private EmbedResult() {} // for gson, don't use otherwise

        public EmbedResult(@NonNull String title, @Nullable String duration, @Nullable PostImage extraImage) {
            this.title = title;
            this.duration = duration;
            this.extraImage = extraImage;
        }
    }

    public interface InvalidateFunction {
        void invalidateView(boolean simple);
    }
    //endregion
}
