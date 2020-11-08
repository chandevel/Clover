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

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.IgnoreFailureCallback;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.NullCall;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, EmbedResult> videoTitleDurCache = new LruCache<>(500);

    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    public EmbeddingEngine() {
        // Media embedders
        embedders.add(new YoutubeEmbedder());
        embedders.add(new StreamableEmbedder());
        embedders.add(new VocarooEmbedder());
        embedders.add(new ClypEmbedder());
        embedders.add(new SoundcloudEmbedder());
        embedders.add(new BandcampEmbedder());
        embedders.add(new VimeoEmbedder());
        embedders.add(new PixivEmbedder());

        // Special embedders
        embedders.add(new QuickLatexEmbedder());
    }

    /**
     * To add a media link parser:<br>
     * 1) Implement the Embedder class.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- The helper method addStandardCalls should be used for most stuff, it takes in all the appropriate stuff you'll need to implement functionality<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- For more complicated stuff, you can extend the necessary items yourself quite easily.<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;- Look at already implemented embedders for hints! Streamable's is probably the easiest to understand for a baseline.
     * 2) Add it to the EmbeddingEngine constructor.
     * 3) Done! Everything else is taken care of for you.<br>
     * <br>
     *
     * @param theme              The theme to style the links with
     * @param post               The post where the links will be found and replaced
     * @param invalidateFunction The entire view to be refreshed after embedding
     * @return A list of enqueued calls that will embed the given post
     */

    public List<Call> embed(
            final Theme theme, @NonNull final Post post, @NonNull final InvalidateFunction invalidateFunction
    ) {
        // These count as embedding, so we do them here
        doAutoLinking(theme, post);
        addPostImages(post);

        // Generate all the calls if embedding is on
        final List<Pair<Call, Callback>> generatedCallPairs = new ArrayList<>();
        if (ChanSettings.enableEmbedding.get() && !post.embedComplete.get()) {
            for (Embedder<?> e : embedders) {
                if (!StringUtils.containsAny(post.comment, e.getShortRepresentations())) continue;
                generatedCallPairs.addAll(e.generateCallPairs(theme, post));
            }
        }

        if (generatedCallPairs.isEmpty()) { // nothing to embed
            if (!post.embedComplete.get()) {
                post.embedComplete.set(true);
                BackgroundUtils.runOnMainThread(() -> invalidateFunction.invalidateView(post));
            }
            return Collections.emptyList();
        }

        // Set up and enqueue all the generated calls
        final int callCount = generatedCallPairs.size();
        final List<Call> calls = new ArrayList<>();
        final AtomicInteger processed = new AtomicInteger();
        final AtomicBoolean setComplete = new AtomicBoolean(true);
        for (final Pair<Call, Callback> c : generatedCallPairs) {
            // enqueue all at the same time, wrapped callback to check when everything's complete
            c.first.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    c.second.onFailure(call, e);
                    setComplete.set(false); // we'll need to re-embed this on a failure
                    checkInvalidate();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response)
                        throws IOException {
                    c.second.onResponse(call, response);
                    checkInvalidate();
                }

                private void checkInvalidate() {
                    if (callCount != processed.incrementAndGet()) return; // still completing calls
                    post.embedComplete.set(setComplete.get());
                    BackgroundUtils.runOnMainThread(() -> invalidateFunction.invalidateView(post));
                }
            });
            calls.add(c.first);
        }

        return calls;
    }

    //region Embedding Helper Functions
    private static void doAutoLinking(Theme theme, @NonNull Post post) {
        final Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(post.comment);
        for (final LinkSpan link : links) {
            final String linkText = TextUtils.substring(post.comment, link.getBeginIndex(), link.getEndIndex());
            final String scheme = linkText.substring(0, linkText.indexOf(':'));
            if (!"http".equals(scheme) && !"https".equals(scheme)) continue; // only autolink URLs, not any random URI
            final PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);
            //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            //we use 500 here for to go below post linkables, but above everything else basically
            post.comment.setSpan(pl,
                    link.getBeginIndex(),
                    link.getEndIndex(),
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );
            post.linkables.add(pl);
        }
    }

    //region Image Inlining
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

    private static void addPostImages(Post post) {
        if (!ChanSettings.parsePostImageLinks.get()) return;
        for (PostLinkable linkable : post.linkables) {
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
                            .isInlined()
                            .size(-1)
                            .build();

                    post.addImage(inlinedImage);

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
    //endregion

    public static <T> List<Pair<Call, Callback>> addStandardEmbedCalls(Embedder<T> embedder, Theme theme, Post post) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, post);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme, post, result, urlPair.first, embedder.getIconBitmap()));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeCall(urlPair.second,
                        embedder,
                        embedder,
                        getStandardResponseResult(theme, post, urlPair.first, embedder.getIconBitmap()),
                        2500,
                        false
                ));
            }
        }
        return calls;
    }

    private static <T> Set<Pair<String, HttpUrl>> generateReplacements(Embedder<T> embedder, Post post) {
        Set<Pair<String, HttpUrl>> result = new HashSet<>();
        Matcher linkMatcher = embedder.getEmbedReplacePattern().matcher(post.comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            result.add(new Pair<>(URL, embedder.generateRequestURL(linkMatcher)));
        }
        return result;
    }

    private static ResponseResult<EmbedResult> getStandardResponseResult(
            Theme theme, Post post, String URL, Bitmap iconBitmap
    ) {
        return new ResponseResult<EmbedResult>() {
            @Override
            public void onFailure(Exception e) {} // don't do anything, let the autolinker take care of it

            @Override
            public void onSuccess(EmbedResult result) {
                //got a result, replace with the result and also cache the result
                videoTitleDurCache.put(URL, result);
                performStandardEmbedding(theme, post, result, URL, iconBitmap);
            }
        };
    }

    private static Pair<Call, Callback> getStandardCachedCallPair(
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
     * Generally don't use this directly, use it through the addStandardEmbedCalls method
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

                // Get any existing linkables in this area and remove them (removes autolinks)
                for (PostLinkable linkable : post.comment.getSpans(index, index + 4, PostLinkable.class)) {
                    if (linkable.type == PostLinkable.Type.LINK && TextUtils.equals(linkable.key,
                            linkable.value.toString()
                    )) {
                        post.comment.removeSpan(linkable);
                        post.linkables.remove(linkable);
                    }
                }

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
        void invalidateView(Post post);
    }
    //endregion
}
