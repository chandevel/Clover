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
package org.floens.chan.chan;


import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import org.floens.chan.Chan;
import org.floens.chan.core.database.DatabaseManager;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.floens.chan.utils.AndroidUtils.sp;

public class ChanParser {
    private static final String TAG = "ChanParser";
    private static final Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]*)");

    private static ChanParser instance = new ChanParser();
    private final DatabaseManager databaseManager;

    public ChanParser() {
        databaseManager = Chan.getDatabaseManager();
    }

    public static ChanParser getInstance() {
        return instance;
    }

    public void parse(Post post) {
        parse(null, post);
    }

    public void parse(Theme theme, Post post) {
        if (theme == null) {
            theme = ThemeHelper.getInstance().getTheme();
        }

        try {
            if (!TextUtils.isEmpty(post.name)) {
                post.name = Parser.unescapeEntities(post.name, false);
            }

            if (!TextUtils.isEmpty(post.subject)) {
                post.subject = Parser.unescapeEntities(post.subject, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!post.parsedSpans) {
            post.parsedSpans = true;
            parseSpans(theme, post);
        }

        if (post.rawComment != null) {
            post.comment = parseComment(theme, post, post.rawComment);
        }
    }

    /**
     * Parse the comment, subject, tripcodes, names etc. as spannables.<br>
     * This is done on a background thread for performance, even when it is UI code.<br>
     * The results will be placed on the Post.*Span members.
     *
     * @param theme Theme to use for parsing
     * @param post  Post to get data from
     */
    private void parseSpans(Theme theme, Post post) {
        boolean anonymize = ChanSettings.anonymize.get();
        boolean anonymizeIds = ChanSettings.anonymizeIds.get();

        if (anonymize) {
            post.name = "Anonymous";
            post.tripcode = "";
        }

        if (anonymizeIds) {
            post.id = "";
        }

        int detailsSizePx = sp(Integer.parseInt(ChanSettings.fontSize.get()) - 4);

        if (!TextUtils.isEmpty(post.subject)) {
            post.subjectSpan = new SpannableString(post.subject);
            // Do not set another color when the post is in stub mode, it sets text_color_secondary
            if (!post.filterStub) {
                post.subjectSpan.setSpan(new ForegroundColorSpanHashed(theme.subjectColor), 0, post.subjectSpan.length(), 0);
            }
        }

        if (!TextUtils.isEmpty(post.name) && !post.name.equals("Anonymous")) {
            post.nameSpan = new SpannableString(post.name);
            post.nameSpan.setSpan(new ForegroundColorSpanHashed(theme.nameColor), 0, post.nameSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            post.tripcodeSpan = new SpannableString(post.tripcode);
            post.tripcodeSpan.setSpan(new ForegroundColorSpanHashed(theme.nameColor), 0, post.tripcodeSpan.length(), 0);
            post.tripcodeSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, post.tripcodeSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.id)) {
            post.idSpan = new SpannableString("  ID: " + post.id + "  ");

            // Stolen from the 4chan extension
            int hash = post.id.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            int idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
            boolean lightColor = (r * 0.299f) + (g * 0.587f) + (b * 0.114f) > 125f;
            int idBgColor = lightColor ? theme.idBackgroundLight : theme.idBackgroundDark;

            post.idSpan.setSpan(new ForegroundColorSpanHashed(idColor), 0, post.idSpan.length(), 0);
            post.idSpan.setSpan(new BackgroundColorSpan(idBgColor), 0, post.idSpan.length(), 0);
            post.idSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, post.idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            post.capcodeSpan = new SpannableString("Capcode: " + post.capcode);
            post.capcodeSpan.setSpan(new ForegroundColorSpanHashed(theme.capcodeColor), 0, post.capcodeSpan.length(), 0);
            post.capcodeSpan.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, post.capcodeSpan.length(), 0);
        }

        post.nameTripcodeIdCapcodeSpan = new SpannableString("");
        if (post.nameSpan != null) {
            post.nameTripcodeIdCapcodeSpan = TextUtils.concat(post.nameTripcodeIdCapcodeSpan, post.nameSpan, " ");
        }

        if (post.tripcodeSpan != null) {
            post.nameTripcodeIdCapcodeSpan = TextUtils.concat(post.nameTripcodeIdCapcodeSpan, post.tripcodeSpan, " ");
        }

        if (post.idSpan != null) {
            post.nameTripcodeIdCapcodeSpan = TextUtils.concat(post.nameTripcodeIdCapcodeSpan, post.idSpan, " ");
        }

        if (post.capcodeSpan != null) {
            post.nameTripcodeIdCapcodeSpan = TextUtils.concat(post.nameTripcodeIdCapcodeSpan, post.capcodeSpan, " ");
        }
    }

    private CharSequence parseComment(Theme theme, Post post, String commentRaw) {
        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.replace("<wbr>", "");

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

    private CharSequence parseNode(Theme theme, Post post, Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            SpannableString spannable = new SpannableString(text);

            detectLinks(theme, post, text, spannable);

            return spannable;
        } else {
            switch (node.nodeName()) {
                case "br": {
                    return "\n";
                }
                case "span": {
                    Element span = (Element) node;

                    SpannableString quote;

                    Set<String> classes = span.classNames();
                    if (classes.contains("deadlink")) {
                        quote = new SpannableString(span.text());
                        quote.setSpan(new ForegroundColorSpanHashed(theme.quoteColor), 0, quote.length(), 0);
                        quote.setSpan(new StrikethroughSpan(), 0, quote.length(), 0);
                    } else if (classes.contains("fortune")) {
                        // html looks like <span class="fortune" style="color:#0893e1"><br><br><b>Your fortune:</b>
                        // manually add these <br>
                        quote = new SpannableString("\n\n" + span.text());

                        String style = span.attr("style");
                        if (!TextUtils.isEmpty(style)) {
                            style = style.replace(" ", "");

                            // private static final Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]*)");
                            Matcher matcher = colorPattern.matcher(style);

                            int hexColor = 0xff0000;
                            if (matcher.find()) {
                                String group = matcher.group(1);
                                if (!TextUtils.isEmpty(group)) {
                                    try {
                                        hexColor = Integer.parseInt(group, 16);
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                            }

                            if (hexColor >= 0 && hexColor <= 0xffffff) {
                                quote.setSpan(new ForegroundColorSpanHashed(0xff000000 + hexColor), 0, quote.length(), 0);
                                quote.setSpan(new StyleSpan(Typeface.BOLD), 0, quote.length(), 0);
                            }
                        }
                    } else if (classes.contains("abbr")) {
                        return null;
                    } else {
                        quote = new SpannableString(span.text());
                        quote.setSpan(new ForegroundColorSpanHashed(theme.inlineQuoteColor), 0, quote.length(), 0);
                        detectLinks(theme, post, span.text(), quote);
                    }

                    return quote;
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

                    PostLinkable pl = new PostLinkable(theme, post, spoiler.text(), spoiler.text(), PostLinkable.Type.SPOILER);
                    link.setSpan(pl, 0, link.length(), 0);
                    post.linkables.add(pl);

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

    private CharSequence parseAnchor(Theme theme, Post post, Element anchor) {
        String href = anchor.attr("href");
        Set<String> classes = anchor.classNames();

        PostLinkable.Type t = null;
        String key = null;
        Object value = null;
        if (classes.contains("quotelink")) {
            if (href.contains("/thread/")) {
                // link to another thread
                PostLinkable.ThreadLink threadLink = null;

                String[] slashSplit = href.split("/");
                if (slashSplit.length == 4) {
                    String board = slashSplit[1];
                    String nums = slashSplit[3];
                    String[] numsSplitted = nums.split("#p");
                    if (numsSplitted.length == 2) {
                        try {
                            int tId = Integer.parseInt(numsSplitted[0]);
                            int pId = Integer.parseInt(numsSplitted[1]);
                            threadLink = new PostLinkable.ThreadLink(board, tId, pId);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                if (threadLink != null) {
                    t = PostLinkable.Type.THREAD;
                    key = anchor.text() + " \u2192"; // arrow to the right
                    value = threadLink;
                }
            } else {
                // normal quote
                int id = -1;

                String[] splitted = href.split("#p");
                if (splitted.length == 2) {
                    try {
                        id = Integer.parseInt(splitted[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }

                if (id >= 0) {
                    t = PostLinkable.Type.QUOTE;
                    key = anchor.text();
                    value = id;
                    post.repliesTo.add(id);

                    // Append OP when its a reply to OP
                    if (id == post.resto) {
                        key += " (OP)";
                    }

                    // Append You when it's a reply to an saved reply
                    if (databaseManager.isSavedReply(post.board, id)) {
                        key += " (You)";
                    }
                }
            }
        } else {
            // normal link
            t = PostLinkable.Type.LINK;
            key = anchor.text();
            value = href;
        }

        if (t != null && key != null && value != null) {
            SpannableString link = new SpannableString(key);
            PostLinkable pl = new PostLinkable(theme, post, key, value, t);
            link.setSpan(pl, 0, link.length(), 0);
            post.linkables.add(pl);

            return link;
        } else {
            return null;
        }
    }

    private void detectLinks(Theme theme, Post post, String text, SpannableString spannable) {
        int startPos = 0;
        int endPos;
        while (true) {
            startPos = text.indexOf("://", startPos);
            if (startPos < 0) break;

            // go back to the first space
            while (startPos > 0 && !isWhitespace(text.charAt(startPos - 1))) {
                startPos--;
            }

            // find the last non whitespace character
            endPos = startPos;
            while (endPos < text.length() - 1 && !isWhitespace(text.charAt(endPos + 1))) {
                endPos++;
            }

            // one past
            endPos++;

            String linkString = text.substring(startPos, endPos);

            PostLinkable pl = new PostLinkable(theme, post, linkString, linkString, PostLinkable.Type.LINK);
            spannable.setSpan(pl, startPos, endPos, 0);
            post.linkables.add(pl);

            startPos = endPos;
        }
    }

    private boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || c == '>'; // consider > as a link separator
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
