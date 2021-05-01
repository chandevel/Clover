package com.github.adamantcheese.chan.features.embedding;

import android.text.SpannableStringBuilder;

import com.github.adamantcheese.chan.core.model.PostImage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;

public abstract class Embeddable {
    // a helper variable to ensure embedding is only performed once
    public final AtomicBoolean embedComplete = new AtomicBoolean(false);
    // all the calls that are generated for this object
    protected final List<Call> embedCalls = new CopyOnWriteArrayList<>();

    /**
     * @return The text that should be embedded for this implementation.
     */
    public abstract SpannableStringBuilder getEmbeddableText();

    /**
     * @param text The text that should be stored for this implementation.
     */
    public abstract void setEmbeddableText(SpannableStringBuilder text);

    /**
     * If your embeddable deals with extra embedded images, they will be provided by the embedding engine with this method.
     * Default implementation ignores these extra image objects.
     *
     * @param images A list of extra images that should be stored in this implementation.
     */
    public void addImageObjects(List<PostImage> images) {}

    /**
     * Clears any embed calls; embedding engine should pick up on this and any in-flight calls will fail
     * If embedding is done, this just clears up any old references
     */
    public void stopEmbedding() {
        for (Call c : embedCalls) {
            c.cancel();
        }
        embedCalls.clear();
    }
}
