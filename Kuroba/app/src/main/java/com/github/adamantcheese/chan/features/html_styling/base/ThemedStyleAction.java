package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.features.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.Map;

public abstract class ThemedStyleAction
        extends AdditionalDataStyleAction {
    private static final String THEME_DATA = "theme";

    public final AdditionalDataStyleAction with(Theme theme) {
        AdditionalDataStyleAction newAction = new AdditionalDataStyleAction() {
            @NonNull
            @Override
            protected CharSequence style(
                    @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
            ) {
                return ThemedStyleAction.this.style(node, text, data);
            }
        };
        newAction.data.put(THEME_DATA, theme);
        return newAction;
    }

    @NonNull
    protected abstract CharSequence style(@NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme);

    @NonNull
    @Override
    protected final CharSequence style(
            @NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data
    ) {
        Theme theme = (Theme) data.get(THEME_DATA);
        if (theme == null) {
            throw new IllegalStateException("Cannot style with missing info! Call with() beforehand!");
        }
        return style(node, text, theme);
    }
}
