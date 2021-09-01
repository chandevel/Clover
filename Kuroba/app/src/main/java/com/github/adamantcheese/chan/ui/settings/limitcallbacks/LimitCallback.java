package com.github.adamantcheese.chan.ui.settings.limitcallbacks;

public interface LimitCallback<T> {
    boolean isInLimit(T entry);

    T getMinimumLimit();

    T getMaximumLimit();
}
