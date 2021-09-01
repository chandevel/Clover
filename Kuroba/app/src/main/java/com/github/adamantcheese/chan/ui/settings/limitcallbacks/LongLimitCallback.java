package com.github.adamantcheese.chan.ui.settings.limitcallbacks;

public abstract class LongLimitCallback
        implements LimitCallback<Long> {
    @Override
    public boolean isInLimit(Long entry) {
        return entry >= getMinimumLimit() && entry <= getMaximumLimit();
    }

    public abstract Long getMinimumLimit();

    public abstract Long getMaximumLimit();
}
