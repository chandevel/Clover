package com.github.adamantcheese.chan.ui.text;

import com.github.adamantcheese.chan.ui.theme.Theme;

/**
 * This is basically just a rename of ChanHighlightSpan, so that removing spans is easy.
 * Max alpha and inverted color if applicable as opposed to a regular highlight from the Android system.
 */
public class FilterHighlightSpan
        extends ChanHighlightSpan {
    public FilterHighlightSpan(Theme theme) {
        super(theme, (byte) 255, true);
    }
}
