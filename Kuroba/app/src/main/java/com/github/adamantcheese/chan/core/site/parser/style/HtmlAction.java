package com.github.adamantcheese.chan.core.site.parser.style;

import android.text.Spanned;
import android.text.SpannedString;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Element;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.CSS_COLOR_ATTR;
import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.CSS_SIZE_ATTR;
import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.INLINE_CSS;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.BLOCK_LINE_BREAK;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.BOLD;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.ITALICIZE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.NEWLINE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.UNDERLINE;

/**
 * This style action handles generic HTML and applies the appropriate rules to it when elements are passed in for styling.
 */
public class HtmlAction
        implements StyleAction {
    // Two maps of rules for this parser, mapping an HTML tag to a list of StyleRules that need to be applied for that tag
    // Maps an element tag to a map of css classes to style rules; ie more specific from just the tag
    private final Map<String, Map<String, ChainStyleAction>> specificRules = new HashMap<>();
    // Maps an element tag to a rule; ie the style always applies to the tag
    private final Map<String, ChainStyleAction> wildcardRules = new HashMap<>();

    public HtmlAction() {
        // required newline rules
        mapTagToRule("p", BLOCK_LINE_BREAK);
        mapTagToRule("div", BLOCK_LINE_BREAK);
        mapTagToRule("br", NEWLINE);

        // simple text
        mapTagToRule("strong", BOLD);
        mapTagToRule("b", BOLD);
        mapTagToRule("strike", STRIKETHROUGH);
        mapTagToRule("i", ITALICIZE);
        mapTagToRule("em", ITALICIZE);
        mapTagToRule("u", UNDERLINE);
        mapTagToRule("font", CSS_COLOR_ATTR, CSS_SIZE_ATTR);
    }

    public void mapTagToRule(String tag, StyleAction... rules) {
        ChainStyleAction ruleForTag = wildcardRules.get(tag);
        if (ruleForTag == null) {
            ruleForTag = new ChainStyleAction(rules);
        } else {
            ChainStyleAction concat = new ChainStyleAction(ruleForTag.actions);
            concat.actions.addAll(Arrays.asList(rules));
            ruleForTag = concat;
        }
        wildcardRules.put(tag, ruleForTag);
    }

    public void mapTagToRule(String tag, String cssClass, StyleAction... rules) {
        Map<String, ChainStyleAction> classMap = specificRules.get(tag);
        if (classMap == null) {
            classMap = new HashMap<>();
            specificRules.put(tag, classMap);
        }

        ChainStyleAction specificForTag = classMap.get(cssClass);
        if (specificForTag == null) {
            specificForTag = new ChainStyleAction(rules);
        } else {
            ChainStyleAction concat = new ChainStyleAction(specificForTag.actions);
            concat.actions.addAll(Arrays.asList(rules));
            specificForTag = concat;
        }
        classMap.put(cssClass, specificForTag);
    }

    @NonNull
    @Override
    public SpannedString style(
            @NonNull Element element,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    ) {
        Map<String, ChainStyleAction> specificsForTag = specificRules.get(element.normalName());
        ChainStyleAction specificForTagClass = null;
        if (specificsForTag != null) {
            for (String n : element.classNames()) {
                specificForTagClass = specificsForTag.get(n);
                if (specificForTagClass != null) break;
            }
        }
        ChainStyleAction wildcardForTag = wildcardRules.get(element.normalName());
        ChainStyleAction actionToTake = specificForTagClass != null ? specificForTagClass : wildcardForTag;

        // add in the inline CSS action if it isn't already in the chain
        if (actionToTake != null) {
            if (!actionToTake.actions.contains(INLINE_CSS)) actionToTake.actions.add(INLINE_CSS);
            return actionToTake.style(element, text, theme, post, callback);
        } else {
            return new SpannedString(text);
        }
    }
}

