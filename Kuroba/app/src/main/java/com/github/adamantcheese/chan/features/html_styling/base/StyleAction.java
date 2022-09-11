package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jsoup.nodes.Node;

/**
 * Allows the ability to change the text or style a given element.
 */
public interface StyleAction {
    /**
     * @param node            The node that is currently being worked on.
     * @param styledInnerText The styled inner text of the node currently being worked on; all children have been processed
     * @return stylized text for this node; can be adding styling to the passed in child inner text or replacing it
     */
    @NonNull
    CharSequence style(@NonNull Node node, @Nullable CharSequence styledInnerText);
}
