package com.github.adamantcheese.chan.core.site.parser.style;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.vdurmont.emoji.EmojiParser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This style action handles an entire HTML document tree of elements and applies the appropriate rules to it when passed in for styling.
 */
public class HtmlDocumentAction
        implements StyleAction {
    private final HtmlElementAction elementAction;

    public HtmlDocumentAction(HtmlElementAction elementAction) {
        this.elementAction = elementAction;
    }

    @NonNull
    @Override
    public SpannedString style(
            @NonNull Element element,
            @Nullable Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    ) {
        if (!(element instanceof Document)) {
            throw new IllegalArgumentException("Passed in non-document element!");
        }
        return processGenericNode(element, theme, post, callback);
    }

    private SpannedString processGenericNode(
            @NonNull Node n, @NonNull Theme theme, @NonNull Post.Builder post, @NonNull PostParser.Callback callback
    ) {
        try {
            if (n instanceof Element) {
                return processElementNode((Element) n, theme, post, callback);
            } else if (n instanceof TextNode) {
                return processTextNode((TextNode) n);
            } else {
                Logger.e(this, "Unknown node instance: " + n.getClass().getName());
            }
        } catch (Exception e) {
            Logger.e(this, "Error parsing element html", e);
        }
        return new SpannedString("");
    }

    private SpannedString processElementNode(
            @NonNull Element node,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    ) {
        SpannableStringBuilder allInnerText = new SpannableStringBuilder();

        for (Node innerNode : node.childNodes()) {
            allInnerText.append(processGenericNode(innerNode, theme, post, callback));
        }

        return elementAction.style((Element) node, allInnerText, theme, post, callback);
    }

    private SpannedString processTextNode(TextNode node) {
        String nodeText = node.getWholeText();
        if (ChanSettings.enableEmoji.get() && !( //emoji parse disable for [code] and [eqn]
                (node.parent() instanceof Element && (((Element) node.parent()).hasClass("prettyprint")))
                        || nodeText.startsWith("[eqn]"))) {
            nodeText = processEmojiMath(nodeText);
        }
        return new SpannedString(nodeText);
    }

    // Modified from 3.20 of Regular Expressions Cookbook, 2nd Edition
    // find that bad boy on LibGen, it's good stuff
    private final Pattern MATH_PATTERN = Pattern.compile("\\[(math|eqn)].*?\\[/\\1]");

    private String processEmojiMath(String text) {
        StringBuilder rebuilder = new StringBuilder();
        Matcher regexMatcher = MATH_PATTERN.matcher(text);
        int lastIndex = 0;
        while (regexMatcher.find()) {
            rebuilder.append(EmojiParser.parseToUnicode(text.substring(lastIndex, regexMatcher.start())));
            rebuilder.append(regexMatcher.group());
            lastIndex = regexMatcher.end();
        }
        rebuilder.append(EmojiParser.parseToUnicode(text.substring(lastIndex)));
        return rebuilder.toString();
    }
}
