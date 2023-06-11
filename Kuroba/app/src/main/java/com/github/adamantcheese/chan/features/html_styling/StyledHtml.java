package com.github.adamantcheese.chan.features.html_styling;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.A_HREF;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.EMOJI;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.NO_OP;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.LINK;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.features.html_styling.base.ChainStyleAction;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlNodeTreeAction;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlTagAction;
import com.github.adamantcheese.chan.ui.helper.ThemeHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Node;

import java.util.*;

public class StyledHtml {
    /**
     * Prepare an html body fragment for parsing.
     *
     * @param htmlBody A string representing the html to parse.
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

    /**
     * Drop-in replacement for Html.fromHtml.
     *
     * @param htmlBody A string representing the html to parse.
     * @param baseUri  The base uri for the html.
     * @return A CharSequence that contains the styled text.
     */
    public static CharSequence fromHtml(String htmlBody, String baseUri) {
        Map<String, StyleAction> defaultTagMappings = new HashMap<>();
        Map<Pair<String, String>, StyleAction> defaultTagClassMappings = new HashMap<>();
        List<StyleAction> defaultTextActions = new ArrayList<>();

        defaultTagMappings.put("a", A_HREF);
        // for these, the last item in the list will be processed first
        defaultTextActions.add(LINK.with(ThemeHelper.getTheme()));
        defaultTextActions.add(EMOJI);

        return fromHtml(htmlBody, baseUri, defaultTagMappings, defaultTagClassMappings, defaultTextActions);
    }

    /**
     * Replacement for Html.fromHtml that allows for extra tag actions to be taken.
     *
     * @param htmlBody              A string representing the html to parse.
     * @param baseUri               The base uri for the html.
     * @param extraTagMappings      Extra tag mappings
     * @param extraTagClassMappings Extra tag-class mappings
     * @param extraTextActions      Extra text element actions
     * @return A CharSequence that contains the styled text.
     */
    public static CharSequence fromHtml(
            String htmlBody,
            String baseUri,
            Map<String, StyleAction> extraTagMappings,
            Map<Pair<String, String>, StyleAction> extraTagClassMappings,
            List<StyleAction> extraTextActions
    ) {
        HtmlTagAction tagAction = new HtmlTagAction();
        tagAction.addDefaultRules();

        for (Map.Entry<String, StyleAction> entry : extraTagMappings.entrySet()) {
            tagAction.mapTagToRule(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Pair<String, String>, StyleAction> entry : extraTagClassMappings.entrySet()) {
            tagAction.mapTagToRule(entry.getKey().first, entry.getKey().second, entry.getValue());
        }
        ChainStyleAction textAction = new ChainStyleAction(NO_OP);
        for (StyleAction action : extraTextActions) {
            textAction = textAction.chain(action);
        }

        return new HtmlNodeTreeAction(tagAction, textAction).style(prepare(htmlBody, baseUri), null);
    }
}
