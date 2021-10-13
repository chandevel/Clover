package com.github.adamantcheese.chan.core.site.parser.style;

import android.text.Spanned;
import android.text.SpannedString;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Node;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.CSS_COLOR_ATTR_FG;
import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.CSS_SIZE_ATTR;
import static com.github.adamantcheese.chan.core.site.parser.style.CSSActions.INLINE_CSS;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.BLOCK_LINE_BREAK;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.BOLD;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.ITALICIZE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.NEWLINE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.UNDERLINE;

/**
 * This style action handles one HTML element and applies the appropriate rules to it when passed in for styling.
 */
public class HtmlElementAction
        implements StyleAction {
    // Two maps of rules for this parser, mapping an HTML tag to a list of StyleRules that need to be applied for that tag
    // Maps an element tag to a map of css classes to style rules; ie more specific from just the tag
    private final Map<String, Map<String, ChainStyleAction>> specificRules = new HashMap<>();
    // Maps an element tag to a rule; ie the style always applies to the tag
    private final Map<String, ChainStyleAction> wildcardRules = new HashMap<>();

    public HtmlElementAction() {
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
        mapTagToRule("font", CSS_COLOR_ATTR_FG, CSS_SIZE_ATTR);
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
            @NonNull Node element,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    ) {
        Map<String, ChainStyleAction> specificsForTag = specificRules.get(element.nodeName());
        ChainStyleAction specificForTagClass = null;
        if (specificsForTag != null) {
            for (String n : classNames(element)) {
                specificForTagClass = specificsForTag.get(n);
                if (specificForTagClass != null) break;
            }
        }
        ChainStyleAction wildcardForTag = wildcardRules.get(element.nodeName());
        ChainStyleAction actionToTake = specificForTagClass != null ? specificForTagClass : wildcardForTag;

        // add in the inline CSS action if it isn't already in the chain
        if (actionToTake != null) {
            if (!actionToTake.actions.contains(INLINE_CSS)) actionToTake.actions.add(INLINE_CSS);
            return actionToTake.style(element, text, theme, post, callback);
        } else {
            return INLINE_CSS.style(element, text, theme, post, callback);
        }
    }

    private static final Pattern classSplit = Pattern.compile("\\s+");

    // copied from Element, but this works with nodes too
    private Set<String> classNames(Node node) {
        String[] names = classSplit.split(node.attr("class").trim());
        Set<String> classNames = new LinkedHashSet<>(Arrays.asList(names));
        classNames.remove(""); // if classNames() was empty, would include an empty class
        return classNames;
    }
}

