package com.github.adamantcheese.chan.ui.text;

import com.github.adamantcheese.chan.ui.theme.Theme;

/**
 * This is basically just a rename of ChanHighlightSpan, so that removing spans is easy.
 * 40% alpha, and don't change the foreground color, matching the emulator's highlighting
 */
public class SearchHighlightSpan
        extends ChanHighlightSpan {
    public SearchHighlightSpan(Theme theme) {
        super(theme, (byte) 102, false);
    }
}
