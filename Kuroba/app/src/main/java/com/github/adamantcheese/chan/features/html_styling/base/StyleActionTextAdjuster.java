package com.github.adamantcheese.chan.features.html_styling.base;

public interface StyleActionTextAdjuster {
    /**
     * Return an adjusted piece of text that will be used when styling with StyleActions.
     * Note that these may have spans attached to them.
     *
     * @param base The base text
     * @return The adjusted text
     */
    CharSequence adjust(CharSequence base);
}
