package com.github.adamantcheese.chan.ui.text;

/**
 * This is basically just a rename of ChanHighlightSpan, so that removing spans is easy.
 * Max alpha and inverted color if applicable as opposed to a regular highlight from the Android system.
 */
public class FilterHighlightSpan
        extends ChanHighlightSpan {
    public FilterHighlightSpan() {
        super((byte) 255, true);
    }
}
