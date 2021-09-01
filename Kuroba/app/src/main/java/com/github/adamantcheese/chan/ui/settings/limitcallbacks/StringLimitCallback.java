package com.github.adamantcheese.chan.ui.settings.limitcallbacks;

public class StringLimitCallback
        implements LimitCallback<String> {
    @Override
    public boolean isInLimit(String entry) {
        return true;
    }

    @Override
    public String getMinimumLimit() {
        return "";
    }

    @Override
    public String getMaximumLimit() {
        return "";
    }
}
