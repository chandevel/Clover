package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.ResponseConverter;
import com.github.adamantcheese.chan.utils.NetUtilsClasses.ResponseProcessor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;

public interface Embedder<T>
        extends ResponseProcessor<EmbedResult, T>, ResponseConverter<T> {
    /**
     * This is MAINLY used for the auto-linker to prevent it from linking stuff that'll get processed by an embedder.
     * This is also used in some helper methods to skip expensive generation steps.
     *
     * @return A list of strings that represent what this embedder does, usually just the name of the host eg youtube
     */
    List<String> getShortRepresentations();

    /**
     * This is used for the helper calls in EmbeddingEngine for a "standard" embed of icon-title-duration.
     *
     * @return A bitmap representing this embedder, if used for the replacement
     */
    Bitmap getIconBitmap();

    /**
     * @return A pattern that will match the text to be replaced with the embed, usually a URL
     */
    Pattern getEmbedReplacePattern();

    /**
     * @param matcher The matcher for the embed pattern above, if needed for generating the URL
     * @return A URL that requests should be sent to in order to retrieve information for the embedding
     */
    HttpUrl generateRequestURL(Matcher matcher);

    /**
     * @param theme The current theme, for post linkables (generally is ThemeHelper.getCurrentTheme())
     * @param post  The post for the embedding, where any embeds should be replaced or any additional images should be attached
     * @return A list of pairs of call/callback that will do the embedding. A post may have more than one thing to be embedded.
     * Calls should NOT be enqueued, as the embedding engine will take care of enqueuing the appropriate call/callback pair.
     */
    List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post);

    /**
     * This is used by helper calls in EmbeddingEngine to automatically convert a response body to an object that can be processed.
     *
     * @param baseURL The URL that this body was retrieved from
     * @param body The body from an OkHttp call
     * @return The body as a proper object that can be processed by the implemented process method below
     */
    @Override
    T convert(HttpUrl baseURL, @Nullable ResponseBody body)
            throws Exception;

    /**
     * This is used by helper calls in EmbeddingEngine to automatically process a returned result.
     *
     * @param response The response to convert into an EmbedResult (usually either a Document or JSONReader)
     * @return An embed result for the call, consisting of a title, duration, and an optional extra post image
     */
    EmbedResult process(T response)
            throws Exception;
}

