package com.github.adamantcheese.chan.ui.settings.limitcallbacks;

public abstract class IntegerLimitCallback
        implements LimitCallback<Integer> {
    @Override
    public boolean isInLimit(Integer entry) {
        return entry >= getMinimumLimit() && entry <= getMaximumLimit();
    }

    public abstract Integer getMinimumLimit();

    @Override
    public abstract Integer getMaximumLimit();
}
