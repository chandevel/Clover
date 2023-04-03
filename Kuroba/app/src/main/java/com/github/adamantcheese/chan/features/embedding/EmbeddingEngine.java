package com.github.adamantcheese.chan.features.embedding;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.*;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.embedders.base.Embedder;
import com.github.adamantcheese.chan.features.embedding.embedders.impl.*;
import com.github.adamantcheese.chan.ui.text.post_linkables.EmbedderLinkLinkable;
import com.github.adamantcheese.chan.ui.text.post_linkables.ParserLinkLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.*;
import com.github.adamantcheese.chan.utils.JavaUtils.NoDeleteArrayList;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import okhttp3.*;

public class EmbeddingEngine
        implements DefaultLifecycleObserver {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Embedder> embedders = new NoDeleteArrayList<>();

    private static final int CACHE_SIZE = 1500;
    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    private LruCache<String, EmbedResult> videoTitleDurCache = new LruCache<>(CACHE_SIZE);

    private static EmbeddingEngine instance;

    /**
     * Create a new engine instance. You should really only need one.
     *
     * @param context             The context to use for cache saving; this should be an Activity instance
     * @param customEmbeddersList A list of embedders to use, instead of the default provided list.
     */
    @SuppressLint("RestrictedApi")
    public EmbeddingEngine(@NonNull LifecycleOwner context, @Nullable List<Embedder> customEmbeddersList) {
        if (instance != null) throw new IllegalArgumentException("Only create one of these!");
        if (customEmbeddersList != null) {
            embedders.addAll(customEmbeddersList);
        }
        for (Embedder e : embedders) {
            e.setup(NetUtils.applicationClient.cookieJar());
        }
        context.getLifecycle().addObserver(this);
        instance = this;
    }

    public static EmbeddingEngine getInstance() {
        if (instance == null) throw new IllegalArgumentException("Need to create one before using it!");
        return instance;
    }

    public static List<Embedder> getDefaultEmbedders() {
        List<Embedder> defaults = new ArrayList<>();

        // Video embedders
        defaults.add(new YoutubeEmbedder());
        defaults.add(new StreamableEmbedder());
        defaults.add(new VimeoEmbedder());

        // Audio embedders
        defaults.add(new VocarooEmbedder());
        defaults.add(new ClypEmbedder());
        defaults.add(new SoundcloudEmbedder());
        defaults.add(new BandcampEmbedder());

        // Image embedders
        defaults.add(new PixivEmbedder());
        defaults.add(new DlsiteEmbedder());
        defaults.add(new ImgurEmbedder());

        // Text embedders
        defaults.add(new StrawpollEmbedder());
        defaults.add(new PastebinEmbedder());

        // Special embedders
        defaults.add(new QuickLatexEmbedder());

        return defaults;
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
     * @param embeddable         The embeddable where the links will be found and replaced
     * @param invalidateFunction The entire view to be refreshed after embedding
     * @return true if this embeddable will be embedded, false otherwise
     */

    public <T extends Embeddable> boolean embed(
            @NonNull final Theme theme,
            @NonNull final T embeddable,
            @NonNull final InvalidateFunction invalidateFunction
    ) {
        if (embeddable.hasCompletedEmbedding()) return false;
        BackgroundUtils.runOnBackgroundThread(() -> embedInternal(theme, embeddable, invalidateFunction));
        return true;
    }

    private <T extends Embeddable> void embedInternal(
            @NonNull final Theme theme,
            @NonNull final T embeddable,
            @NonNull final InvalidateFunction invalidateFunction
    ) {
        embeddable.setComplete(); // prevent duplicate calls
        embeddable.stopEmbedding();

        //create a new copy so we can edit the text from the embeddable without modifying it in-place
        SpannableStringBuilder embedCopy = new SpannableStringBuilder(embeddable.getEmbeddableText());

        // Some embedders can generate images, like album thumbnails, so add to those here
        List<PostImage> generatedImages = new NoDeleteArrayList<>();

        // Generate all the calls
        List<Pair<Call, Callback>> generatedCallPairs = new NoDeleteArrayList<>();
        for (Embedder e : embedders) {
            if (!e.shouldEmbed(embedCopy)) continue;
            generatedCallPairs.addAll(e.generateCallPairs(theme, embedCopy, generatedImages, videoTitleDurCache));
        }

        if (generatedCallPairs.isEmpty()) {
            onEmbeddingComplete(embeddable, embedCopy, generatedImages, invalidateFunction);
            return; // this view will be embedded, but not with any network calls so we exit early
        }

        // Set up and enqueue all the generated calls
        int callCount = generatedCallPairs.size();
        AtomicInteger processed = new AtomicInteger();
        for (Pair<Call, Callback> c : generatedCallPairs) {
            // enqueue all at the same time, wrapped callback to check when everything's complete
            c.first.enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    c.second.onFailure(call, e);
                    embeddable.setIncomplete(); // we've failed embedding and need a redo
                    checkInvalidate();
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) {
                    try {
                        c.second.onResponse(call, response);
                    } catch (IOException e) {
                        c.second.onFailure(call, e);
                    }
                    checkInvalidate();
                }

                private void checkInvalidate() {
                    if (callCount != processed.incrementAndGet()) return; // still completing calls
                    // even if partial, just run the complete
                    onEmbeddingComplete(embeddable, embedCopy, generatedImages, invalidateFunction);
                }
            });
            embeddable.addEmbedCall(c.first);
        }
    }

    private <T extends Embeddable> void onEmbeddingComplete(
            @NonNull final T embeddable,
            SpannableStringBuilder modifiableCopy,
            List<PostImage> generatedImages,
            InvalidateFunction invalidateFunction
    ) {
        // clear out any overlapping embed postlinkables
        ParserLinkLinkable[] autolinks = modifiableCopy.getSpans(0, modifiableCopy.length(), ParserLinkLinkable.class);
        EmbedderLinkLinkable[] embedlinks =
                modifiableCopy.getSpans(0, modifiableCopy.length(), EmbedderLinkLinkable.class);

        // remove autolinks if an embed link exists
        for (ParserLinkLinkable autolink : autolinks) {
            for (EmbedderLinkLinkable embed : embedlinks) {
                if (autolink.value.equals(embed.value)) {
                    modifiableCopy.removeSpan(autolink);
                }
            }
        }

        // finish up embedding, set completed objects and send invalidate if necessary
        mainHandler.post(() -> {
            // make the modified copy unmodifiable
            embeddable.setEmbeddableText(new SpannedString(modifiableCopy));
            embeddable.addImageObjects(generatedImages);
            embeddable.stopEmbedding();
            invalidateFunction.invalidate();
        });
    }

    //region Embedding Helper Functions
    public static List<Pair<Call, Callback>> addStandardEmbedCalls(
            Embedder embedder,
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            LruCache<String, EmbedResult> videoTitleDurCache
    ) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, commentCopy);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(embedder,
                        theme,
                        commentCopy,
                        generatedImages,
                        result,
                        urlPair.first
                ));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeCall(NetUtils.applicationClient,
                        urlPair.second,
                        embedder,
                        getStandardResponseResult(embedder,
                                theme,
                                commentCopy,
                                generatedImages,
                                videoTitleDurCache,
                                urlPair.first
                        ),
                        null,
                        NetUtilsClasses.ONE_DAY_CACHE,
                        embedder.getExtraHeaders(),
                        embedder.getTimeoutMillis(),
                        false
                ));
            }
        }
        return calls;
    }

    private static Set<Pair<String, HttpUrl>> generateReplacements(
            Embedder embedder, SpannableStringBuilder comment
    ) {
        Set<Pair<String, HttpUrl>> result = new HashSet<>();
        Matcher linkMatcher = embedder.getEmbedReplacePattern().matcher(comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            result.add(new Pair<>(URL, embedder.generateRequestURL(linkMatcher)));
        }
        return result;
    }

    private static ResponseResult<EmbedResult> getStandardResponseResult(
            Embedder embedder,
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            LruCache<String, EmbedResult> videoTitleDurCache,
            String URL
    ) {
        return new ResponseResult<EmbedResult>() {
            @Override
            public void onFailure(Exception e) {
                Logger.vd("EmbeddingEngine", "Embed failed for " + URL, e);
            }

            @Override
            public void onSuccess(EmbedResult result) {
                //got a result, replace with the result and also cache the result
                if (embedder.shouldCacheResults()) {
                    videoTitleDurCache.put(URL, result);
                }
                performStandardEmbedding(theme, commentCopy, generatedImages, result, URL, embedder.getIconBitmap());
            }
        };
    }

    private static Pair<Call, Callback> getStandardCachedCallPair(
            Embedder embedder,
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            EmbedResult result,
            String URL
    ) {
        // in case of embed replacement where the replaced text isn't in the form of a link,
        // a dummy URL needs to be used otherwise okhttp3 will crash because it looks for a valid link
        String dummyURL = "https://example.com";
        return new Pair<>(new NullCall(HttpUrl.get(URL.startsWith("http") ? URL : dummyURL)),
                new IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        performStandardEmbedding(theme,
                                commentCopy,
                                generatedImages,
                                result,
                                URL,
                                embedder.getIconBitmap()
                        );
                    }
                }
        );
    }

    /**
     * Performs a "standard" embed of an icon followed by a title and optional duration.<br>
     * Additionally, this method adds in any embed post image results to the given post.<br>
     * <br>
     * Generally don't use this directly, use it through the addStandardEmbedCalls method
     */
    public static void performStandardEmbedding(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostImage> generatedImages,
            @NonNull EmbedResult parseResult,
            @NonNull String URL,
            final Bitmap icon
    ) {
        // commentCopy is safe to synchronize on
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (commentCopy) {
            StringUtils.replaceAll(commentCopy, () -> URL, (source) -> {
                CharSequence replacement = StringUtils.prependIcon(getAppContext(),
                        new SpannableStringBuilder()
                                .append(" ")
                                .append(parseResult.title)
                                .append(TextUtils.isEmpty(parseResult.duration) ? "" : " " + parseResult.duration),
                        icon,
                        sp(ChanSettings.fontSize.get())
                );

                EmbedderLinkLinkable pl = new EmbedderLinkLinkable(theme, URL);
                return span(replacement, pl);
            }, true);
        }
        // if linking is enabled, add in any processed inlines
        if (ChanSettings.parsePostImageLinks.get()) {
            generatedImages.addAll(parseResult.extraImages);
        }
    }
    //endregion

    public void clearCache() {
        videoTitleDurCache.evictAll();
        CACHE_FILE.delete();
    }

    private static final Type LRU_TYPE = new TypeToken<Map<String, EmbedResult>>() {}.getType();
    private static final File CACHE_FILE = new File(getCacheDir(), "video_title_cache.json");

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        try (FileReader reader = new FileReader(CACHE_FILE)) {
            //restore parsed media title stuff
            Map<String, EmbedResult> titles = AppModule.gson.fromJson(reader, LRU_TYPE);
            //reconstruct
            videoTitleDurCache = new LruCache<>(CACHE_SIZE);
            for (Map.Entry<String, EmbedResult> entry : titles.entrySet()) {
                videoTitleDurCache.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            CACHE_FILE.delete(); // bad file probably
        }
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        //store parsed media title stuff, extra prevention of unneeded API calls
        try (FileWriter writer = new FileWriter(CACHE_FILE)) {
            AppModule.gson.toJson(videoTitleDurCache.snapshot(), LRU_TYPE, writer);
        } catch (Exception e) {
            CACHE_FILE.delete();
        }
    }

    public interface InvalidateFunction {
        void invalidate();
    }
}
