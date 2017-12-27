/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.site.common;


import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.span.AbsoluteSizeSpanHashed;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.utils.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.sp;

public class FutabaChanParser implements ChanParser {
    private static final String TAG = "FutabaChanParser";
    private static final String SAVED_REPLY_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    public static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right

    private FutabaChanParserHandler handler;

    public FutabaChanParser(FutabaChanParserHandler handler) {
        this.handler = handler;
    }

    @Override
    public Post parse(Theme theme, Post.Builder builder, Callback callback) {
        if (theme == null) {
            theme = ThemeHelper.getInstance().getTheme();
        }

        try {
            if (!TextUtils.isEmpty(builder.name)) {
                builder.name = Parser.unescapeEntities(builder.name, false);
            }

            if (!TextUtils.isEmpty(builder.subject)) {
                builder.subject = Parser.unescapeEntities(builder.subject, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        parseSpans(theme, builder);

        if (builder.comment != null) {
            builder.comment = parseComment(theme, builder, builder.comment, callback);
        } else {
            builder.comment = "";
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

        if (!TextUtils.isEmpty(builder.name) && (!builder.name.equals(defaultName) || ChanSettings.showAnonymousName.get())) {
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

            // Stolen from the 4chan extension
            int hash = builder.posterId.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            //noinspection NumericOverflow
            int idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
            boolean lightColor = (r * 0.299f) + (g * 0.587f) + (b * 0.114f) > 125f;
            int idBgColor = lightColor ? theme.idBackgroundLight : theme.idBackgroundDark;

            idSpan.setSpan(new ForegroundColorSpanHashed(idColor), 0, idSpan.length(), 0);
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

            total = TextUtils.concat(texts.toArray(new CharSequence[texts.size()]));
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing comment html", e);
        }

        return total;
    }

    private CharSequence parseNode(Theme theme, Post.Builder post, Callback callback, Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            SpannableString spannable = new SpannableString(text);

            ChanParserHelper.detectLinks(theme, post, text, spannable);

            return spannable;
        } else {
            switch (node.nodeName()) {
                case "p": {
                    // Recursively call parseNode with the nodes of the paragraph.
                    List<Node> innerNodes = node.childNodes();
                    List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

                    for (Node innerNode : innerNodes) {
                        CharSequence nodeParsed = parseNode(theme, post, callback, innerNode);
                        if (nodeParsed != null) {
                            texts.add(nodeParsed);
                        }
                    }

                    if (node.nextSibling() != null) {
                        texts.add("\n");
                    }

                    CharSequence res = TextUtils.concat(texts.toArray(new CharSequence[texts.size()]));

                    return handler.handleParagraph(this, theme, post, res, (Element) node);
                }
                case "br": {
                    return "\n";
                }
                case "span": {
                    return handler.handleSpan(this, theme, post, (Element) node);
                }
                case "table": {
                    return handler.handleTable(this, theme, post, (Element) node);
                }
                case "strong": {
                    return handler.handleStrong(this, theme, post, (Element) node);
                }
                case "a": {
                    CharSequence anchor = parseAnchor(theme, post, callback, (Element) node);
                    if (anchor != null) {
                        return anchor;
                    } else {
                        return ((Element) node).text();
                    }
                }
                case "s": {
                    return handler.handleStrike(this, theme, post, (Element) node);
                }
                case "pre": {
                    return handler.handlePre(this, theme, post, (Element) node);
                }
                default: {
                    // Unknown tag, add the inner part
                    if (node instanceof Element) {
                        return ((Element) node).text();
                    } else {
                        return null;
                    }
                }
            }
        }
    }

    private CharSequence parseAnchor(Theme theme, Post.Builder post, Callback callback,
                                     Element anchor) {
        FutabaChanParserHandler.Link handlerLink =
                handler.handleAnchor(this, theme, post, anchor);

        if (handlerLink != null) {
            if (handlerLink.type == PostLinkable.Type.THREAD) {
                handlerLink.key += EXTERN_THREAD_LINK_SUFFIX;
            }

            if (handlerLink.type == PostLinkable.Type.QUOTE) {
                int postNo = (int) handlerLink.value;
                post.addReplyTo(postNo);

                // Append (OP) when its a reply to OP
                if (postNo == post.opId) {
                    handlerLink.key += OP_REPLY_SUFFIX;
                }

                // Append (You) when it's a reply to an saved reply
                if (callback.isSaved(postNo)) {
                    handlerLink.key += SAVED_REPLY_SUFFIX;
                }
            }

            SpannableString link = new SpannableString(handlerLink.key);
            PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
            link.setSpan(pl, 0, link.length(), 0);
            post.addLinkable(pl);

            return link;
        } else {
            return null;
        }
    }


}
