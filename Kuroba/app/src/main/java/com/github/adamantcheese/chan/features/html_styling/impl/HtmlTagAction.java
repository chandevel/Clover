package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonCSSActions.FONT;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonCSSActions.INLINE_CSS;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.features.html_styling.base.ChainStyleAction;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;

import org.jsoup.nodes.Node;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This style action handles one HTML element and applies the appropriate rules to it when passed in for styling.
 */
public class HtmlTagAction
        implements StyleAction {
    // Two maps of rules for this parser, mapping an HTML tag to a list of StyleRules that need to be applied for that tag
    // Maps an element tag to a map of css classes to style rules; ie more specific from just the tag
    public final Map<String, Map<String, ChainStyleAction>> specificRules = new HashMap<>();
    // Maps an element tag to a rule; ie the style always applies to the tag
    public final Map<String, ChainStyleAction> wildcardRules = new HashMap<>();

    public HtmlTagAction(boolean addDefaultRules) {
        if (!addDefaultRules) return;
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
        mapTagToRule("font", FONT);
        mapTagToRule("tt", MONOSPACE);
    }

    public void mapTagToRule(String tag, StyleAction... rules) {
        ChainStyleAction ruleForTag = wildcardRules.get(tag);
        if (ruleForTag == null) {
            ruleForTag = new ChainStyleAction(NO_OP);
        }
        for (StyleAction rule : rules) {
            ruleForTag = ruleForTag.chain(rule);
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
            specificForTag = new ChainStyleAction(NO_OP);
        }
        for (StyleAction rule : rules) {
            specificForTag = specificForTag.chain(rule);
        }
        classMap.put(cssClass, specificForTag);
    }

    @NonNull
    @Override
    public CharSequence style(
            @NonNull Node element, @Nullable CharSequence text
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

        CharSequence result = text;
        if (actionToTake != null) {
            result = actionToTake.style(element, text);
        }
        // CSS styling is always applied
        return INLINE_CSS.style(element, result);
    }

    /**
     * Merge this tag action with another tag action, so that all rules are conserved and overridden, if needed
     *
     * @param other The other action to merge with this one
     * @return A new tag action with the rules of both actions in it
     */
    public HtmlTagAction mergeWith(HtmlTagAction other) {
        HtmlTagAction merged = new HtmlTagAction(false);
        merged.wildcardRules.putAll(wildcardRules);
        merged.specificRules.putAll(specificRules);
        for (String wildcardTag : other.wildcardRules.keySet()) {
            ChainStyleAction addingAction = other.wildcardRules.get(wildcardTag);
            ChainStyleAction existingAction = merged.wildcardRules.get(wildcardTag);
            if (existingAction != null) {
                existingAction.chain(addingAction);
            } else {
                merged.wildcardRules.put(wildcardTag, addingAction);
            }
        }
        for (String specificMapTag : other.specificRules.keySet()) {
            Map<String, ChainStyleAction> addingActions = other.specificRules.get(specificMapTag);
            Map<String, ChainStyleAction> existingActions = merged.specificRules.get(specificMapTag);
            if (existingActions != null) {
                for (String specificTag : addingActions.keySet()) {
                    ChainStyleAction addingAction = addingActions.get(specificTag);
                    ChainStyleAction existingAction = existingActions.get(specificTag);
                    if (existingAction != null) {
                        existingAction.chain(addingAction);
                    } else {
                        existingActions.put(specificTag, addingAction);
                    }
                }
            } else {
                merged.specificRules.put(specificMapTag, addingActions);
            }
        }
        return merged;
    }

    private static final Pattern classSplit = Pattern.compile("\\s+");

    // copied from Element, but this works with nodes too
    @NonNull
    private Set<String> classNames(@NonNull Node node) {
        String[] names = classSplit.split(node.attr("class").trim());
        Set<String> classNames = new LinkedHashSet<>(Arrays.asList(names));
        classNames.remove(""); // if classNames() was empty, would include an empty class
        return classNames;
    }
}

