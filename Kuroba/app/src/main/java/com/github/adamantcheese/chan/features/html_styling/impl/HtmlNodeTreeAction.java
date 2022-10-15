package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.A_HREF;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.EMOJI;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.LINK;

import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.features.html_styling.base.ChainStyleAction;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;

import java.util.Collections;
import java.util.Map;

/**
 * This style action handles an entire HTML document tree of elements and applies the right rules to it when passed in for styling.
 * You can technically pass in any node to have it return styled text however, as it handles arbitrary node trees.
 * Nodes are processed depth-first with breadth awareness; that is, the deepest nodes are processed first, and the
 * element action is applied to the concatenated tree below that element to style the entire subtree.
 * <p>
 * For a drop-in replacement of Html.fromHtml, use the fromHtml defined in this class.
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

    /**
     * Prepare an html body fragment for parsing.
     *
     * @param htmlBody The html to parse.
     * @param baseUri  The base uri for the html.
     * @return A prepared node that can be used in style().
     */
    public static Node prepare(String htmlBody, String baseUri) {
        if (htmlBody.startsWith("<![CDATA[") && htmlBody.endsWith("]]>")) {
            htmlBody = htmlBody.substring("<![CDATA[".length(), htmlBody.length() - "]]>".length());
        }
        // clean up wbr tags before parsing, otherwise things get difficult and complicated
        return Jsoup.parse(htmlBody.replaceAll("<wbr>", ""), baseUri == null ? "" : baseUri);
    }

    public static CharSequence fromHtml(String htmlBody, String baseUri) {
        return fromHtml(htmlBody, baseUri, Collections.emptyMap());
    }

    public static CharSequence fromHtml(String htmlBody, String baseUri, Map<String, StyleAction> extraMappings) {
        HtmlTagAction tagAction = new HtmlTagAction();
        tagAction.addDefaultRules();
        tagAction.mapTagToRule("a", A_HREF);
        for (Map.Entry<String, StyleAction> entry : extraMappings.entrySet()) {
            tagAction.mapTagToRule(entry.getKey(), entry.getValue());
        }
        StyleAction textAction = new ChainStyleAction(LINK.with(ThemeHelper.getTheme())).chain(EMOJI);
        return new HtmlNodeTreeAction(tagAction, textAction).style(prepare(htmlBody, baseUri), null);
    }

    @NonNull
    @Override
    public CharSequence style(@NonNull Node node, @Nullable CharSequence styledInnerText) {
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
