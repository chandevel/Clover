package com.github.adamantcheese.chan.features.embedding;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.core.app.ComponentActivity;
import androidx.core.util.Pair;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.IgnoreFailureCallback;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.NullCall;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ThreadLink;
import com.github.adamantcheese.chan.features.embedding.embedders.BandcampEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.ClypEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.Embedder;
import com.github.adamantcheese.chan.features.embedding.embedders.PixivEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.QuickLatexEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.SoundcloudEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.StreamableEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.VimeoEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.VocarooEmbedder;
import com.github.adamantcheese.chan.features.embedding.embedders.YoutubeEmbedder;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.JavaUtils.NoDeleteArrayList;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Response;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class EmbeddingEngine
        implements LifecycleObserver {
    private static EmbeddingEngine instance;
    private static Handler mainHandler;
    private static final List<Embedder> embedders = new NoDeleteArrayList<>();

    private static final int CACHE_SIZE = 1500;
    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    private static LruCache<String, EmbedResult> videoTitleDurCache = new LruCache<>(CACHE_SIZE);

    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    @SuppressLint("RestrictedApi")
    private EmbeddingEngine(@NonNull ComponentActivity context) {
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

        for(Embedder e : embedders) {
            e.setup(NetUtils.applicationClient.cookieJar());
        }

        context.getLifecycle().addObserver(this);
    }

    /**
     * Initialize the engine. This can be called multiple times and the instance will be replaced as necessary
     *
     * @param context The context to use for cache saving; this should be an Activity instance
     */
    public static void initEngine(@NonNull ComponentActivity context) {
        instance = new EmbeddingEngine(context);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * @return The instance of the engine, once initialized.
     */
    public static EmbeddingEngine getInstance() {
        if (BuildConfig.DEBUG && instance == null)
            throw new UnsupportedOperationException("EmbeddingEngine must be initialized before use!");
        return instance;
    }

    public List<Embedder> getEmbedders() {
        return Collections.unmodifiableList(embedders);
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
        if (embeddable.embedComplete.get()) return false;
        embeddable.embedComplete.set(true); // prevent duplicate calls
        embeddable.stopEmbedding();

        SpannableStringBuilder autoLinkCopy = new SpannableStringBuilder(embeddable.getEmbeddableText());
        //clear out any existing embed spans that were generated by this class
        for (PostLinkable l : autoLinkCopy.getSpans(0, autoLinkCopy.length(), PostLinkable.class)) {
            if (l.type == PostLinkable.Type.EMBED) {
                autoLinkCopy.removeSpan(l);
            }
        }

        // These count as embedding, so we do them here
        List<PostLinkable> generatedAutoLinks = new ArrayList<>(generateAutoLinks(theme, autoLinkCopy));
        List<PostLinkable> possibleImageLinks = new ArrayList<>(generatedAutoLinks);
        for (PostLinkable l : autoLinkCopy.getSpans(0, autoLinkCopy.length(), PostLinkable.class)) {
            if (l.type == PostLinkable.Type.LINK) possibleImageLinks.add(l);
        }
        List<PostImage> generatedImages = new NoDeleteArrayList<>(generatePostImages(possibleImageLinks));

        SpannableStringBuilder embedCopy = new SpannableStringBuilder(autoLinkCopy);
        List<PostLinkable> generatedLinkables = new ArrayList<>(generatedAutoLinks);

        // Generate all the calls if embedding is on
        List<Pair<Call, Callback>> generatedCallPairs = new NoDeleteArrayList<>();
        if (ChanSettings.enableEmbedding.get()) {
            for (Embedder e : embedders) {
                if (!e.shouldEmbed(embedCopy)) continue;
                generatedCallPairs.addAll(e.generateCallPairs(theme, embedCopy, generatedLinkables, generatedImages));
            }
        }

        if (generatedCallPairs.isEmpty()) {
            onEmbeddingComplete(embeddable, autoLinkCopy, generatedAutoLinks, generatedImages, invalidateFunction);
            return false; // technically this view will be embedded, but not with any network calls so we return false
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
                    embeddable.embedComplete.set(false); // we've failed embedding and need a redo
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
                    if (!embeddable.embedComplete.get()) {
                        // partial complete, use autolinks and ignore the rest as a full-fail
                        onEmbeddingComplete(embeddable,
                                autoLinkCopy,
                                generatedAutoLinks,
                                generatedImages,
                                invalidateFunction
                        );
                        return;
                    }
                    onEmbeddingComplete(embeddable, embedCopy, generatedLinkables, generatedImages, invalidateFunction);
                }
            });
            embeddable.embedCalls.add(c.first);
        }
        return true;
    }

    private <T extends Embeddable> void onEmbeddingComplete(
            @NonNull final T embeddable,
            SpannableStringBuilder modifiableCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            InvalidateFunction invalidateFunction
    ) {
        // check if we need to do any processing to invalidate, as a shortcut
        if (generatedImages.isEmpty() && generatedLinkables.isEmpty()) return;

        // remove any temp linkables (embeds that don't really generate a linkable, but let it pass through this function)
        // notably QuickLatexEmbedder uses this
        for (Iterator<PostLinkable> iterator = generatedLinkables.iterator(); iterator.hasNext(); ) {
            PostLinkable linkable = iterator.next();
            if (linkable.type == PostLinkable.Type.EMBED_TEMP) {
                iterator.remove();
            }
        }

        // clear out any overlapping embed postlinkables from the generated set
        // split up auto/embed links
        List<PostLinkable> autolinks = new ArrayList<>();
        List<PostLinkable> embedlinks = new ArrayList<>();
        for (PostLinkable linkable : generatedLinkables) {
            if (linkable.key.equals(linkable.value)) {
                autolinks.add(linkable);
            } else {
                embedlinks.add(linkable);
            }
        }

        // remove autolinks if an embed link exists
        for (PostLinkable autolink : autolinks) {
            for (PostLinkable embed : embedlinks) {
                if (autolink.value.equals(embed.value)) {
                    modifiableCopy.removeSpan(autolink);
                }
            }
        }

        mainHandler.post(() -> {
            embeddable.setEmbeddableText(modifiableCopy);
            embeddable.addImageObjects(generatedImages);
            embeddable.stopEmbedding();
            invalidateFunction.invalidate();
        });
    }

    //region Embedding Helper Functions
    private static List<PostLinkable> generateAutoLinks(Theme theme, SpannableStringBuilder comment) {
        List<PostLinkable> generated = new ArrayList<>();
        Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(comment);
        for (LinkSpan link : links) {
            // if there's already a PostLinkable here, skip over it, we'd otherwise be stacking stuff up
            if (comment.getSpans(link.getBeginIndex(), link.getEndIndex(), PostLinkable.class).length > 0) continue;
            String linkText = TextUtils.substring(comment, link.getBeginIndex(), link.getEndIndex());
            String scheme = linkText.substring(0, linkText.indexOf(':'));
            if (!"http".equals(scheme) && !"https".equals(scheme)) continue; // only autolink URLs, not any random URI
            PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.EMBED);

            // double check however and set up "archive" links here in place of regular links
            // this allows the person to pick any archive they want, regardless of if it actually is the link in question
            try {
                String domain = HttpUrl.get(linkText).topPrivateDomain();
                if (domain == null) throw new IllegalArgumentException("No domain?");
                ExternalSiteArchive a = ArchivesManager.getInstance().archiveForDomain(domain);
                if (a != null) {
                    Loadable resolved = a.resolvable().resolveLoadable(a, HttpUrl.get(linkText));
                    if (resolved != null) {
                        Object value = new ThreadLink(resolved.boardCode, resolved.no, resolved.markedNo);
                        pl = new PostLinkable(theme, linkText, value, PostLinkable.Type.ARCHIVE);
                    }
                }
            } catch (Exception ignored) {}

            //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            //we use 500 here for to go below post linkables, but above everything else basically
            comment.setSpan(pl,
                    link.getBeginIndex(),
                    link.getEndIndex(),
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );
            generated.add(pl);
        }
        return generated;
    }

    //region Image Inlining
    // matches stuff like file.jpg or file?format=jpg&name=orig
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)(?:\\.|\\?.+=)(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac|wav)(?:.*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac", "wav"};

    private static List<PostImage> generatePostImages(List<PostLinkable> linkables) {
        if (!ChanSettings.parsePostImageLinks.get()) return Collections.emptyList();
        List<PostImage> generated = new ArrayList<>();
        for (PostLinkable linkable : linkables) {
            Matcher matcher = IMAGE_URL_PATTERN.matcher(linkable.value.toString());
            if (matcher.matches()) {
                boolean noThumbnail = StringUtils.endsWithAny(linkable.value.toString(), noThumbLinkSuffixes);
                String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";

                HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);
                // ignore saucenao links, not actual images
                if (imageUrl == null || imageUrl.host().equals("saucenao.com")) {
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

                generated.add(inlinedImage);

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
        return generated;
    }
    //endregion

    public static List<Pair<Call, Callback>> addStandardEmbedCalls(
            Embedder embedder,
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages
    ) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        Set<Pair<String, HttpUrl>> toReplace = generateReplacements(embedder, commentCopy);

        for (Pair<String, HttpUrl> urlPair : toReplace) {
            EmbedResult result = videoTitleDurCache.get(urlPair.first);
            if (result != null) {
                // we've previously cached this embed and we don't need additional information; ignore failures because there's no actual call going on
                calls.add(getStandardCachedCallPair(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        urlPair.first,
                        embedder.getIconBitmap()
                ));
            } else {
                // we haven't cached this embed, or we need additional information
                calls.add(NetUtils.makeCall(NetUtils.applicationClient,
                        urlPair.second,
                        embedder,
                        getStandardResponseResult(theme,
                                commentCopy,
                                generatedLinkables,
                                generatedImages,
                                urlPair.first,
                                embedder
                        ),
                        null,
                        NetUtilsClasses.ONE_DAY_CACHE,
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
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            String URL,
            Embedder embedder
    ) {
        return new ResponseResult<EmbedResult>() {
            @Override
            public void onFailure(Exception e) {
                Logger.vd("EmbeddingEngine", "Embed failed for " + URL, e);
            } // don't do anything, let the autolinker take care of it

            @Override
            public void onSuccess(EmbedResult result) {
                //got a result, replace with the result and also cache the result
                if (embedder.shouldCacheResults()) {
                    videoTitleDurCache.put(URL, result);
                }
                performStandardEmbedding(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        URL,
                        embedder.getIconBitmap()
                );
            }
        };
    }

    private static Pair<Call, Callback> getStandardCachedCallPair(
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            EmbedResult result,
            String URL,
            Bitmap iconBitmap
    ) {
        return new Pair<>(new NullCall(HttpUrl.get(URL)), new IgnoreFailureCallback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                performStandardEmbedding(theme,
                        commentCopy,
                        generatedLinkables,
                        generatedImages,
                        result,
                        URL,
                        iconBitmap
                );
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
            Theme theme,
            SpannableStringBuilder commentCopy,
            List<PostLinkable> generatedLinkables,
            List<PostImage> generatedImages,
            @NonNull EmbedResult parseResult,
            @NonNull String URL,
            final Bitmap icon
    ) {
        int index = 0;
        while (true) { // this will always break eventually
            synchronized (commentCopy) {
                // search from the last known replacement location
                index = TextUtils.indexOf(commentCopy, URL, index);
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
                PostLinkable pl = new PostLinkable(theme, replacement, URL, PostLinkable.Type.EMBED);
                replacement.setSpan(pl,
                        0,
                        replacement.length(),
                        ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                );

                // replace the proper section of the comment with the link
                commentCopy.replace(index, index + URL.length(), replacement);
                generatedLinkables.add(pl);

                // if linking is enabled, add in any processed inlines
                if (ChanSettings.parsePostImageLinks.get() && parseResult.extraImage != null) {
                    generatedImages.add(parseResult.extraImage);
                }
                // update the index to the next location
                index = index + replacement.length();
            }
        }
    }
    //endregion

    public void clearCache() {
        videoTitleDurCache.evictAll();
        cacheFile.delete();
    }

    private static final Type lruType = new TypeToken<Map<String, EmbedResult>>() {}.getType();
    private static final File cacheFile = new File(getCacheDir(), "video_title_cache.json");

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onStart() {
        try (FileReader reader = new FileReader(cacheFile)) {
            //restore parsed media title stuff
            Map<String, EmbedResult> titles = AppModule.gson.fromJson(reader, lruType);
            //reconstruct
            videoTitleDurCache = new LruCache<>(CACHE_SIZE);
            for (Map.Entry<String, EmbedResult> entry : titles.entrySet()) {
                videoTitleDurCache.put(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            cacheFile.delete(); // bad file probably
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onStop() {
        //store parsed media title stuff, extra prevention of unneeded API calls
        try (FileWriter writer = new FileWriter(cacheFile)) {
            AppModule.gson.toJson(videoTitleDurCache.snapshot(), lruType, writer);
        } catch (Exception e) {
            cacheFile.delete();
        }
    }

    public interface InvalidateFunction {
        void invalidate();
    }
}
