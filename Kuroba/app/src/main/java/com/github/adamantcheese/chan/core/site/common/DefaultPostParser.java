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
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.CommentParserHelper;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;
import com.vdurmont.emoji.EmojiParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class DefaultPostParser
        implements PostParser {
    private static final String TAG = "DefaultPostParser";

    private CommentParser commentParser;

    public DefaultPostParser(CommentParser commentParser) {
        this.commentParser = commentParser;
    }

    @Override
    public Post parse(Theme theme, Post.Builder builder, Callback callback) {
        if (theme == null) {
            theme = ThemeHelper.getTheme();
        }

        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject, false);
        }

        parseSpans(theme, builder);

        if (builder.comment != null) {
            builder.comment = parseComment(theme, builder, builder.comment, callback);
        } else {
            builder.comment = new SpannableString("");
        }

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
    private void parseSpans(Theme theme, Post.Builder builder) {
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

        int detailsSizePx = sp(Integer.parseInt(ChanSettings.fontSize.get()) - 4);

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
            idSpan = new SpannableString("  ID: " + builder.posterId + "  ");

            int idBgColor = builder.isLightColor ? theme.idBackgroundLight : theme.idBackgroundDark;

            idSpan.setSpan(new ForegroundColorSpanHashed(builder.idColor), 0, idSpan.length(), 0);
            idSpan.setSpan(new BackgroundColorSpan(idBgColor), 0, idSpan.length(), 0);
            idSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            capcodeSpan = new SpannableString("Capcode: " + builder.moderatorCapcode);
            capcodeSpan.setSpan(new ForegroundColorSpanHashed(theme.capcodeColor), 0, capcodeSpan.length(), 0);
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

    private CharSequence parseComment(Theme theme, Post.Builder post, CharSequence commentRaw, Callback callback) {
        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.toString().replace("<wbr>", "");

            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();
            List<CharSequence> texts = new ArrayList<>(nodes.size());

            for (Node node : nodes) {
                CharSequence nodeParsed = parseNode(theme, post, callback, node);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            total = TextUtils.concat(texts.toArray(new CharSequence[0]));
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing comment html", e);
        }

        CommentParserHelper.addPostImages(post);

        return total;
    }

    private CharSequence parseNode(Theme theme, Post.Builder post, Callback callback, Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            if (ChanSettings.enableEmoji.get() && !( //emoji parse disable for [code] and [eqn]
                    (node.parent() instanceof Element && (((Element) node.parent()).hasClass("prettyprint")))
                            || text.startsWith("[eqn]"))) {
                text = processEmojiMath(text);
            }
            //we need to replace youtube links with their titles before linkifying anything else
            //because the string itself changes as a result of the titles shrinking/expanding the string length
            //this would mess up the rest of the spans if we did it afterwards, so we do it as the first step
            SpannableString spannable;
            if (ChanSettings.parseYoutubeTitles.get()) {
                spannable = CommentParserHelper.replaceYoutubeLinks(theme, post, text);
                CommentParserHelper.detectLinks(theme, post, spannable.toString(), spannable);
            } else {
                spannable = new SpannableString(text);
                CommentParserHelper.detectLinks(theme, post, text, spannable);
            }

            return spannable;
        } else if (node instanceof Element) {
            String nodeName = node.nodeName();
            String styleAttr = node.attr("style");
            if (!styleAttr.isEmpty() && !nodeName.equals("span")) {
                nodeName = nodeName + '-' + styleAttr.split(":")[1].trim();
            }

            // Recursively call parseNode with the nodes of the paragraph.
            List<Node> innerNodes = node.childNodes();
            List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

            for (Node innerNode : innerNodes) {
                CharSequence nodeParsed = parseNode(theme, post, callback, innerNode);
                if (nodeParsed != null) {
                    texts.add(nodeParsed);
                }
            }

            CharSequence allInnerText = TextUtils.concat(texts.toArray(new CharSequence[0]));

            CharSequence result =
                    commentParser.handleTag(callback, theme, post, nodeName, allInnerText, (Element) node);
            if (result != null) {
                return result;
            } else {
                return allInnerText;
            }
        } else {
            Logger.e(TAG, "Unknown node instance: " + node.getClass().getName());
            return ""; // ?
        }
    }

    //This method parses emoji but only as long as the text isn't in a [math] block; this can be extended as necessary
    //
    //Text with math not at the start is "offset", so the loop processes alternating items starting at index 0
    //  This covers the case when there are no math tags as well, as the split will return a single item array and the loop runs once
    //Text with math at the start is not "offset", so the loop processes alternating items starting at index 1, as index 0 is covered by [3]
    //  This covers the case when there are only math tags as well, as the split returns a single item array processed by [3] and the loop is skipped
    private String processEmojiMath(String text) {
        String[] split = text.split("\\[/?math]");
        StringBuilder rebuilder = new StringBuilder();
        boolean offset = true;
        if (text.startsWith("[math]")) {
            rebuilder.append("[math]").append(split[0]).append("[/math]"); //[3]
            offset = false;
        }
        for (int i = (offset ? 0 : 1); i < split.length; i++) {
            if ((i - (offset ? 0 : 1)) % 2 == 0) {
                rebuilder.append(EmojiParser.parseToUnicode(split[i])); //[1]
            } else {
                rebuilder.append("[math]").append(split[i]).append("[/math]"); //[2]
            }
        }
        return rebuilder.toString();
    }
}
