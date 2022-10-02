package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows styling with arbitrary data at some specified point in the future. You can set up your styling method beforehand
 * and then call with() to provide the data needed when you have it.
 */
public abstract class AdditionalDataStyleAction
        implements StyleAction {
    protected final Map<String, Object> data = new HashMap<>();

    @NonNull
    protected abstract CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
    );

    @NonNull
    @Override
    public final CharSequence style(@NonNull Node node, @Nullable CharSequence styledInnerText) {
        return style(node, styledInnerText, data);
    }
}
