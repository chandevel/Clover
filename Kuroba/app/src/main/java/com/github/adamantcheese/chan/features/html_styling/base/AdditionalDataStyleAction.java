package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Node;

import java.util.Map;

/**
 * Allows styling with arbitrary data at some specified point in the future. You can set up your styling method beforehand
 * and then call with() to provide the data needed when you have it.
 */
public abstract class AdditionalDataStyleAction
        implements StyleAction {
    protected Map<String, Object> data;

    @CallSuper
    protected AdditionalDataStyleAction with(Map<String, Object> data) {
        this.data = data;
        return this;
    }

    @NonNull
    protected abstract CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
    );

    @NonNull
    @Override
    public CharSequence style(@NonNull Node node, @Nullable CharSequence text) {
        if (data == null) {
            throw new IllegalStateException("Cannot style with null theme! Call with() beforehand!");
        }
        return style(node, text, data);
    }
}
