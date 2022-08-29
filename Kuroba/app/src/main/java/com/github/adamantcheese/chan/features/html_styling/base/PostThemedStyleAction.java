package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.HashMap;
import java.util.Map;

public abstract class PostThemedStyleAction
        extends ThemedStyleAction {
    private static final String POST_DATA = "post";
    private static final String POST_CALLBACK_DATA = "post_callback";

    public AdditionalDataStyleAction with(Theme theme, Post.Builder post, PostParser.Callback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(ThemedStyleAction.THEME_DATA, theme);
        data.put(POST_DATA, post);
        data.put(POST_CALLBACK_DATA, callback);
        return super.with(data);
    }

    @NonNull
    protected abstract CharSequence style(
            @NonNull Node node,
            @Nullable CharSequence text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    );

    @NonNull
    @Override
    protected CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme
    ) {
        Post.Builder post = (Post.Builder) data.get(POST_DATA);
        PostParser.Callback callback = (PostParser.Callback) data.get(POST_CALLBACK_DATA);
        if (post == null || callback == null) {
            throw new IllegalStateException("Cannot style with null post or callback! Call with() beforehand!");
        }
        return style(node, text, theme, post, callback);
    }
}
