/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.site.common;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.vdurmont.emoji.EmojiParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class DefaultPostParser
        implements PostParser {
    private final CommentParser commentParser;
    @Inject
    private FilterEngine filterEngine;

    // All of these have one matching group associated with the text they need to work
    // This negative lookbehind and negative lookahead are just so it doesn't match too much stuff, experimentally determined
    // not preceded by /, ", l, &, : and not followed by ;
    // otherwise match @num, #num, and $num
    private final Pattern extraQuotePattern = Pattern.compile("(?<![/\"l&:])[@#](\\d+)(?!;)");
    private final Pattern extraSpoilerPattern = Pattern.compile("\\[spoiler\\](.*?)\\[/spoiler\\]");
    private final Pattern boldPattern = Pattern.compile("\\*\\*(.+)\\*\\*");
    private final Pattern italicPattern = Pattern.compile("\\*(.+)\\*");
    private final Pattern codePattern = Pattern.compile("`(.+)`");
    private final Pattern strikePattern = Pattern.compile("~~(.+)~~");

    public DefaultPostParser(CommentParser commentParser) {
        this.commentParser = commentParser;
        inject(this);
    }

    @Override
    public Post parse(@NonNull Theme theme, Post.Builder builder, List<Filter> filters, Callback callback) {
        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject, false);
        }

        parseInfoSpans(theme, builder);
        builder.comment = parseComment(theme, builder, callback);

        // process any removed posts, and remove any linkables/spans attached
        for (PostLinkable l : builder.getLinkables()) {
            if (l.type == PostLinkable.Type.QUOTE) {
                if (callback.isRemoved((int) l.value)) {
                    builder.repliesToNos.remove((int) l.value);
                    builder.comment.setSpan(new StrikethroughSpan(),
                            builder.comment.getSpanStart(l),
                            builder.comment.getSpanEnd(l),
                            0
                    );
                    builder.comment.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            showToast(widget.getContext(), "This post has been removed.");
                        }
                    }, builder.comment.getSpanStart(l), builder.comment.getSpanEnd(l), 0);
                    builder.comment.removeSpan(l);
                }
            }
        }

        processPostFilter(filters, builder);

        return builder.build();
    }

    /**
     * Parse the comment, subject, tripcodes, names etc. as spannables.<br>
     * This is done on a background thread for performance, even when it is UI code.<br>
     * The results will be placed on the Post.*Span members.
     *
     * @param theme   Theme to use for parsing
     * @param builder Post builder to get data from
     */
    private void parseInfoSpans(Theme theme, Post.Builder builder) {
        boolean anonymize = ChanSettings.anonymize.get();
        boolean anonymizeIds = ChanSettings.anonymizeIds.get();

        final String defaultName = "Anonymous";
        if (anonymize) {
            builder.name(defaultName);
            builder.tripcode("");
        }

        if (anonymizeIds) {
            builder.posterId("");
        }

        SpannableString subjectSpan = null;
        SpannableString nameSpan = null;
        SpannableString tripcodeSpan = null;
        SpannableString idSpan = null;
        SpannableString capcodeSpan = null;

        int detailsSizePx = sp(ChanSettings.fontSize.get() - 4);

        if (!TextUtils.isEmpty(builder.subject)) {
            subjectSpan = new SpannableString(builder.subject);
            // Do not set another color when the post is in stub mode, it sets text_color_secondary
            if (!builder.filterStub) {
                subjectSpan.setSpan(new ForegroundColorSpanHashed(theme.subjectColor), 0, subjectSpan.length(), 0);
            }
        }

        if (!TextUtils.isEmpty(builder.name) && (!builder.name.equals(defaultName)
                || ChanSettings.showAnonymousName.get())) {
            nameSpan = new SpannableString(builder.name);
            nameSpan.setSpan(new ForegroundColorSpanHashed(theme.nameColor), 0, nameSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.tripcode)) {
            tripcodeSpan = new SpannableString(builder.tripcode);
            tripcodeSpan.setSpan(new ForegroundColorSpanHashed(theme.nameColor), 0, tripcodeSpan.length(), 0);
            tripcodeSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, tripcodeSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.posterId)) {
            idSpan = new SpannableString("  " + builder.posterId + "  ");
            idSpan.setSpan(new ForegroundColorSpanHashed(getContrastColor(builder.idColor)), 0, idSpan.length(), 0);
            idSpan.setSpan(new BackgroundColorSpan(builder.idColor), 0, idSpan.length(), 0);
            idSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            capcodeSpan = new SpannableString(StringUtils.caseAndSpace(builder.moderatorCapcode, null));
            int accentColor = getAttrColor(theme.accentColor.accentStyleId, R.attr.colorAccent);
            capcodeSpan.setSpan(new ForegroundColorSpanHashed(accentColor), 0, capcodeSpan.length(), 0);
            capcodeSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, capcodeSpan.length(), 0);
        }

        CharSequence nameTripcodeIdCapcodeSpan = new SpannableString("");
        if (nameSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, nameSpan, " ");
        }

        if (tripcodeSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, tripcodeSpan, " ");
        }

        if (idSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, idSpan, " ");
        }

        if (capcodeSpan != null) {
            nameTripcodeIdCapcodeSpan = TextUtils.concat(nameTripcodeIdCapcodeSpan, capcodeSpan, " ");
        }

        builder.spans(subjectSpan, nameTripcodeIdCapcodeSpan);
    }

    private SpannableStringBuilder parseComment(@NonNull Theme theme, Post.Builder post, Callback callback) {
        SpannableStringBuilder total = new SpannableStringBuilder("");

        try {
            String comment = post.comment.toString().replace("<wbr>", "");
            // modifiers for HTML
            if (ChanSettings.parseExtraQuotes.get()) {
                comment = extraQuotePattern.matcher(comment).replaceAll(commentParser.createQuoteElementString(post));
            }
            if (ChanSettings.parseExtraSpoilers.get()) {
                comment = extraSpoilerPattern.matcher(comment).replaceAll("<s>$1</s>");
            }
            if (ChanSettings.mildMarkdown.get()) {
                comment = boldPattern.matcher(comment).replaceAll("<b>$1</b>");
                comment = italicPattern.matcher(comment).replaceAll("<i>$1</i>");
                comment = codePattern.matcher(comment).replaceAll("<pre class=\"prettyprint\">$1</pre>");
                comment = strikePattern.matcher(comment).replaceAll("<strike>$1</strike>");
            }

            for (Node node : Jsoup.parseBodyFragment(comment).body().childNodes()) {
                total.append(parseNode(theme, post, callback, node));
            }
        } catch (Exception e) {
            Logger.e(this, "Error parsing comment html", e);
        }

        return total;
    }

    private SpannableStringBuilder parseNode(@NonNull Theme theme, Post.Builder post, Callback callback, Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).getWholeText();
            if (ChanSettings.enableEmoji.get() && !( //emoji parse disable for [code] and [eqn]
                    (node.parent() instanceof Element && (((Element) node.parent()).hasClass("prettyprint")))
                            || text.startsWith("[eqn]"))) {
                text = processEmojiMath(text);
            }
            return new SpannableStringBuilder(text);
        } else if (node instanceof Element) {
            // Recursively call parseNode with the nodes of the paragraph.
            List<Node> innerNodes = node.childNodes();
            List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

            for (Node innerNode : innerNodes) {
                texts.add(parseNode(theme, post, callback, innerNode));
            }

            CharSequence allInnerText = TextUtils.concat(texts.toArray(new CharSequence[0]));

            CharSequence result =
                    commentParser.handleTag(callback, theme, post, node.nodeName(), allInnerText, (Element) node);
            return new SpannableStringBuilder(result != null ? result : "");
        } else {
            Logger.e(this, "Unknown node instance: " + node.getClass().getName());
            return new SpannableStringBuilder(""); // ?
        }
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

    private void processPostFilter(List<Filter> filters, Post.Builder post) {
        for (Filter f : filters) {
            FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(f.action);
            if (filterEngine.matches(f, post)) {
                switch (action) {
                    case COLOR:
                        post.filter(f.color, false, false, false, f.applyToReplies, f.onlyOnOP, f.applyToSaved);
                        break;
                    case HIDE:
                        post.filter(0, true, false, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case REMOVE:
                        post.filter(0, false, true, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case WATCH:
                        post.filter(0, false, false, true, false, true, false);
                        break;
                }
            }
        }
    }
}
