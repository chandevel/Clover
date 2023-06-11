package com.github.adamantcheese.chan.features.theme;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An abstract class that specifies that any data view that this data represents has a "highlighted" state which can be toggled.
 */
public abstract class Highlightable {
    public final AtomicBoolean shouldHighlight = new AtomicBoolean(false);
}
