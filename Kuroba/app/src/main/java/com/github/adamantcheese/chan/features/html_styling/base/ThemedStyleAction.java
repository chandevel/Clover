package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.HashMap;
import java.util.Map;

public abstract class ThemedStyleAction
        extends AdditionalDataStyleAction {
    protected static final String THEME_DATA = "theme";

    public AdditionalDataStyleAction with(Theme theme) {
        Map<String, Object> data = new HashMap<>();
        data.put(THEME_DATA, theme);
        return super.with(data);
    }

    @NonNull
    protected abstract CharSequence style(@NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme);

    @NonNull
    @Override
    protected CharSequence style(@NonNull Node node, @Nullable CharSequence text, @NonNull Map<String, Object> data) {
        Theme theme = (Theme) data.get(THEME_DATA);
        if (theme == null) {
            throw new IllegalStateException("Cannot style with null theme! Call with() beforehand!");
        }
        return style(node, text, theme);
    }
}
