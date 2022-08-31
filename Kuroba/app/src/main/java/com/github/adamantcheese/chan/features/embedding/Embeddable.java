package com.github.adamantcheese.chan.features.embedding;

import android.text.*;

import com.github.adamantcheese.chan.core.model.PostImage;

import java.util.List;

import okhttp3.Call;

public interface Embeddable {
    /**
     * @return The text that should be embedded for this implementation.
     */
    Spanned getEmbeddableText();

    /**
     * @param text The text that should be stored for this implementation.
     */
    void setEmbeddableText(Spanned text);

    void addEmbedCall(Call call);

    List<Call> getEmbedCalls();

    /**
     * If your embeddable deals with extra embedded images, they will be provided by the embedding engine with this method.
     * Default implementation ignores these extra image objects.
     *
     * @param images A list of extra images that should be stored in this implementation.
     */
    default void addImageObjects(List<PostImage> images) {}

    /**
     * Clears any embed calls; embedding engine should pick up on this and any in-flight calls will fail
     * If embedding is done, this just clears up any old references
     */
    default void stopEmbedding() {
        for (Call c : getEmbedCalls()) {
            c.cancel();
        }
        getEmbedCalls().clear();
    }

    default void setComplete() {
        Spannable toUpdate = new SpannableString(getEmbeddableText());
        toUpdate.setSpan(new EmbedCompleteSpan(), 0, 0, 0);
        setEmbeddableText(new SpannedString(toUpdate));
    }

    default void setIncomplete() {
        Spannable toUpdate = new SpannableString(getEmbeddableText());
        EmbedCompleteSpan[] spans = toUpdate.getSpans(0, toUpdate.length(), EmbedCompleteSpan.class);
        for (EmbedCompleteSpan span : spans) {
            toUpdate.removeSpan(span);
        }
        setEmbeddableText(new SpannedString(toUpdate));
    }

    default boolean hasCompletedEmbedding() {
        return getEmbeddableText().getSpans(0, getEmbeddableText().length(), EmbedCompleteSpan.class).length == 1;
    }
}
