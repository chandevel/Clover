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


import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.span.AbsoluteSizeSpanHashed;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.utils.Logger;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.floens.chan.utils.AndroidUtils.sp;

public class FutabaChanParser implements ChanParser {
    private static final String TAG = "FutabaChanParser";
    private static final String SAVED_REPLY_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";

    private final LinkExtractor linkExtractor = LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    private FutabaChanParserHandler handler;

    public FutabaChanParser(FutabaChanParserHandler handler) {
        this.handler = handler;
    }

    @Override
    public Post parse(Theme theme, Post.Builder builder) {
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
            builder.comment = parseComment(theme, builder, builder.comment);
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

    private CharSequence parseComment(Theme theme, Post.Builder post, CharSequence commentRaw) {
        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.toString().replace("<wbr>", "");

            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();
            List<CharSequence> texts = new ArrayList<>(nodes.size());

            for (Node node : nodes) {
                CharSequence nodeParsed = parseNode(theme, post, node);
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

    private CharSequence parseNode(Theme theme, Post.Builder post, Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            SpannableString spannable = new SpannableString(text);

            detectLinks(theme, post, text, spannable);

            return spannable;
        } else {
            switch (node.nodeName()) {
                case "p": {
                    List<Node> innerNodes = node.childNodes();
                    List<CharSequence> texts = new ArrayList<>(innerNodes.size() + 1);

                    for (Node innerNode : innerNodes) {
                        CharSequence nodeParsed = parseNode(theme, post, innerNode);
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
                    Element table = (Element) node;

                    List<CharSequence> parts = new ArrayList<>();
                    Elements tableRows = table.getElementsByTag("tr");
                    for (int i = 0; i < tableRows.size(); i++) {
                        Element tableRow = tableRows.get(i);
                        if (tableRow.text().length() > 0) {
                            Elements tableDatas = tableRow.getElementsByTag("td");
                            for (int j = 0; j < tableDatas.size(); j++) {
                                Element tableData = tableDatas.get(j);

                                SpannableString tableDataPart = new SpannableString(tableData.text());
                                if (tableData.getElementsByTag("b").size() > 0) {
                                    tableDataPart.setSpan(new StyleSpan(Typeface.BOLD), 0, tableDataPart.length(), 0);
                                    tableDataPart.setSpan(new UnderlineSpan(), 0, tableDataPart.length(), 0);
                                }

                                parts.add(tableDataPart);

                                if (j < tableDatas.size() - 1) {
                                    parts.add(": ");
                                }
                            }

                            if (i < tableRows.size() - 1) {
                                parts.add("\n");
                            }
                        }
                    }

                    SpannableString tableTotal = new SpannableString(TextUtils.concat(parts.toArray(new CharSequence[parts.size()])));
                    tableTotal.setSpan(new ForegroundColorSpanHashed(theme.inlineQuoteColor), 0, tableTotal.length(), 0);
                    tableTotal.setSpan(new AbsoluteSizeSpanHashed(sp(12f)), 0, tableTotal.length(), 0);

                    return tableTotal;
                }
                case "strong": {
                    Element strong = (Element) node;

                    SpannableString red = new SpannableString(strong.text());
                    red.setSpan(new ForegroundColorSpanHashed(theme.quoteColor), 0, red.length(), 0);
                    red.setSpan(new StyleSpan(Typeface.BOLD), 0, red.length(), 0);

                    return red;
                }
                case "a": {
                    CharSequence anchor = parseAnchor(theme, post, (Element) node);
                    if (anchor != null) {
                        return anchor;
                    } else {
                        return ((Element) node).text();
                    }
                }
                case "s": {
                    Element spoiler = (Element) node;

                    SpannableString link = new SpannableString(spoiler.text());

                    PostLinkable pl = new PostLinkable(theme, spoiler.text(), spoiler.text(), PostLinkable.Type.SPOILER);
                    link.setSpan(pl, 0, link.length(), 0);
                    post.addLinkable(pl);

                    return link;
                }
                case "pre": {
                    Element pre = (Element) node;

                    Set<String> classes = pre.classNames();
                    if (classes.contains("prettyprint")) {
                        String text = getNodeText(pre);
                        SpannableString monospace = new SpannableString(text);
                        monospace.setSpan(new TypefaceSpan("monospace"), 0, monospace.length(), 0);
                        monospace.setSpan(new AbsoluteSizeSpanHashed(sp(12f)), 0, monospace.length(), 0);
                        return monospace;
                    } else {
                        return pre.text();
                    }
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

    private CharSequence parseAnchor(Theme theme, Post.Builder post, Element anchor) {
        FutabaChanParserHandler.Link handlerLink = handler.getLink(this, theme, post, anchor);

        if (handlerLink != null) {
            SpannableString link = new SpannableString(handlerLink.key);
            PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
            link.setSpan(pl, 0, link.length(), 0);
            post.addLinkable(pl);

            if (handlerLink.type == PostLinkable.Type.THREAD) {
                handlerLink.key += " \u2192"; // arrow to the right
            }

            if (handlerLink.type == PostLinkable.Type.QUOTE) {
                int postNo = (int) handlerLink.value;
                post.addReplyTo(postNo);

                // Append OP when its a reply to OP
                if (postNo == post.opId) {
                    handlerLink.key += OP_REPLY_SUFFIX;
                }

                // Append You when it's a reply to an saved reply
                // TODO(multisite)
                /*if (databaseManager.getDatabaseSavedReplyManager().isSaved(post.board.code, id)) {
                    key += SAVED_REPLY_SUFFIX;
                }*/
            }

            return link;
        } else {
            return null;
        }
    }

    public void detectLinks(Theme theme, Post.Builder post, String text, SpannableString spannable) {
        // use autolink-java lib to detect links
        final Iterable<LinkSpan> links = linkExtractor.extractLinks(text);
        for (final LinkSpan link : links) {
            final String linkText = text.substring(link.getBeginIndex(), link.getEndIndex());
            final PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);
            spannable.setSpan(pl, link.getBeginIndex(), link.getEndIndex(), 0);
            post.addLinkable(pl);
        }
    }

    // Below code taken from org.jsoup.nodes.Element.text(), but it preserves <br>
    private String getNodeText(Element node) {
        final StringBuilder accum = new StringBuilder();
        new NodeTraversor(new NodeVisitor() {
            public void head(Node node, int depth) {
                if (node instanceof TextNode) {
                    TextNode textNode = (TextNode) node;
                    appendNormalisedText(accum, textNode);
                } else if (node instanceof Element) {
                    Element element = (Element) node;
                    if (accum.length() > 0 &&
                            element.isBlock() &&
                            !lastCharIsWhitespace(accum))
                        accum.append(" ");

                    if (element.tag().getName().equals("br")) {
                        accum.append("\n");
                    }
                }
            }

            public void tail(Node node, int depth) {
            }
        }).traverse(node);
        return accum.toString().trim();
    }

    private static boolean lastCharIsWhitespace(StringBuilder sb) {
        return sb.length() != 0 && sb.charAt(sb.length() - 1) == ' ';
    }

    private static void appendNormalisedText(StringBuilder accum, TextNode textNode) {
        String text = textNode.getWholeText();

        if (!preserveWhitespace(textNode.parent())) {
            text = normaliseWhitespace(text);
            if (lastCharIsWhitespace(accum))
                text = stripLeadingWhitespace(text);
        }
        accum.append(text);
    }

    private static String normaliseWhitespace(String text) {
        text = StringUtil.normaliseWhitespace(text);
        return text;
    }

    private static String stripLeadingWhitespace(String text) {
        return text.replaceFirst("^\\s+", "");
    }

    private static boolean preserveWhitespace(Node node) {
        // looks only at this element and one level up, to prevent recursion & needless stack searches
        if (node != null && node instanceof Element) {
            Element element = (Element) node;
            return element.tag().preserveWhitespace() ||
                    element.parent() != null && element.parent().tag().preserveWhitespace();
        }
        return false;
    }
}
