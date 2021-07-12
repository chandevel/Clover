package com.github.adamantcheese.chan.ui.theme;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An interface that specifies that any data view that this data represents has a "highlighted" state which can be toggled.
 */
public interface Highlightable {
    AtomicBoolean shouldHighlight = new AtomicBoolean(false);
}
