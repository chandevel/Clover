package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.Map;

public abstract class FilterPostThemedStyleAction
        extends AdditionalDataStyleAction {
    private static final String THEME_DATA = "theme";
    private static final String FILTER_DATA = "filters";
    private static final String POST_DATA = "post";
    private static final String POST_CALLBACK_DATA = "post_callback";

    public AdditionalDataStyleAction with(
            Theme theme, Filters filters, Post.Builder post, PostParser.PostParserCallback callback
    ) {
        return new AdditionalDataStyleAction() {
            @NonNull
            @Override
            protected CharSequence style(
                    @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
            ) {
                return FilterPostThemedStyleAction.this.style(node, text, data);
            }

            @NonNull
            @Override
            public CharSequence style(@NonNull Node node, @Nullable CharSequence styledInnerText) {
                this.data.put(THEME_DATA, theme);
                this.data.put(FILTER_DATA, filters);
                this.data.put(POST_DATA, post);
                this.data.put(POST_CALLBACK_DATA, callback);
                return super.style(node, styledInnerText);
            }
        };
    }

    @NonNull
    protected abstract CharSequence style(
            @NonNull Node node,
            @Nullable CharSequence text,
            @NonNull Theme theme,
            @NonNull Filters filters,
            @NonNull Post.Builder post,
            @NonNull PostParser.PostParserCallback callback
    );

    @NonNull
    @Override
    protected CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
    ) {
        Theme theme = (Theme) data.get(THEME_DATA);
        Filters filters = (Filters) data.get(FILTER_DATA);
        Post.Builder post = (Post.Builder) data.get(POST_DATA);
        PostParser.PostParserCallback callback = (PostParser.PostParserCallback) data.get(POST_CALLBACK_DATA);
        if (theme == null || filters == null || post == null || callback == null) {
            throw new IllegalStateException("Cannot style with missing info! Call with() beforehand!");
        }
        return style(node, text, theme, filters, post, callback);
    }
}
