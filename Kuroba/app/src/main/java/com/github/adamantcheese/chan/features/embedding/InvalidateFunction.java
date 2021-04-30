package com.github.adamantcheese.chan.features.embedding;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

public interface InvalidateFunction<T extends Embeddable> {
    void invalidateView(@NonNull Theme theme, @NonNull T embeddable);
}
