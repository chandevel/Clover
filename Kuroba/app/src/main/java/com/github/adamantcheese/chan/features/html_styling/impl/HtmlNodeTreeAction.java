package com.github.adamantcheese.chan.features.html_styling.impl;

import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * This style action handles an entire HTML document tree of elements and applies the right rules to it when passed in for styling.
 * You can technically pass in any node to have it return styled text however, as it handles arbitrary node trees.
 * Nodes are processed depth-first with breadth awareness; that is, the deepest nodes are processed first, and the
 * element action is applied to the concatenated tree below that element to style the entire subtree
 */
public class HtmlNodeTreeAction
        implements StyleAction {
    private final StyleAction tagAction;
    private final StyleAction textAction;

    /**
     * @param tagAction  The StyleAction to be applied for each node in the tree.
     * @param textAction The StyleAction to be applied for every terminal text node in the tree.
     */
    public HtmlNodeTreeAction(StyleAction tagAction, StyleAction textAction) {
        this.tagAction = tagAction;
        this.textAction = textAction;
    }

    public CharSequence style(String htmlBody, String baseUri) {
        // clean up wbr tags before parsing, otherwise things get difficult and complicated
        return style(Jsoup.parse(htmlBody.replaceAll("<wbr>", ""), baseUri), null);
    }

    @NonNull
    @Override
    public CharSequence style(@NonNull Node node, @Nullable CharSequence text) {
        try {
            if (node instanceof Element) {
                return processElementNode((Element) node);
            } else if (node instanceof TextNode) {
                return textAction.style(node, ((TextNode) node).getWholeText());
            } else {
                Logger.e(this, "Unknown node instance: " + node.getClass().getName());
            }
        } catch (Exception e) {
            Logger.e(this, "Error parsing element html", e);
        }
        return "";
    }

    @NonNull
    private CharSequence processElementNode(@NonNull Element node) {
        SpannableStringBuilder allInnerText = new SpannableStringBuilder();

        for (Node innerNode : node.childNodes()) {
            allInnerText.append(style(innerNode, null));
        }

        return tagAction.style(node, allInnerText);
    }
}
