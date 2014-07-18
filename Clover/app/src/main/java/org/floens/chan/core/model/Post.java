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
package org.floens.chan.core.model;

import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TypefaceSpan;

import org.floens.chan.ChanApplication;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.PostLinkable.Type;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.ThemeHelper;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Contains all data needed to represent a single post.
 */
public class Post {
    public String board;
    public int no = -1;
    public int resto = -1;
    public boolean isOP = false;
    public String date;
    public String name = "";
    public CharSequence comment = "";
    public String subject = "";
    public String tim;
    public String ext;
    public String filename;
    public int replies = -1;
    public int imageWidth;
    public int imageHeight;
    public boolean hasImage = false;
    public String thumbnailUrl;
    public String imageUrl;
    public boolean sticky = false;
    public boolean closed = false;
    public String tripcode = "";
    public String id = "";
    public String capcode = "";
    public String country = "";
    public String countryName = "";
    public long time = 0;
    public String email = "";
    public boolean isSavedReply = false;
    public String title = "";
    public int fileSize;
    public int images = -1;
    public boolean deleted = false;

    /**
     * This post replies to the these ids
     */
    public List<Integer> repliesTo = new ArrayList<>();

    /**
     * These ids replied to this post
     */
    public List<Integer> repliesFrom = new ArrayList<>();

    public final ArrayList<PostLinkable> linkables = new ArrayList<>();

    public boolean parsedSpans = false;
    public SpannableString subjectSpan;
    public SpannableString nameSpan;
    public SpannableString tripcodeSpan;
    public SpannableString idSpan;
    public SpannableString capcodeSpan;

    /**
     * The PostView the Post is currently bound to.
     */
    private PostView linkableListener;
    private String rawComment;

    public Post() {
    }

    public void setComment(String e) {
        rawComment = e;
    }

    public void setLinkableListener(PostView listener) {
        linkableListener = listener;
    }

    public PostView getLinkableListener() {
        return linkableListener;
    }

    /**
     * Finish up the data
     *
     * @return false if this data is invalid
     */
    public boolean finish(Loadable loadable) {
        if (board == null)
            return false;

        if (no < 0 || resto < 0 || date == null)
            return false;

        isOP = resto == 0;

        if (isOP && (replies < 0 || images < 0))
            return false;

        if (ext != null) {
            hasImage = true;
        }

        if (hasImage) {
            if (filename == null || tim == null || ext == null || imageWidth <= 0 || imageHeight <= 0)
                return false;

            thumbnailUrl = ChanUrls.getThumbnailUrl(board, tim);
            imageUrl = ChanUrls.getImageUrl(board, tim, ext);
            filename = Parser.unescapeEntities(filename, false);
        }

        if (rawComment != null) {
            comment = parseComment(rawComment);
        }

        try {
            if (!TextUtils.isEmpty(name)) {
                name = Parser.unescapeEntities(name, false);
            }

            if (!TextUtils.isEmpty(subject)) {
                subject = Parser.unescapeEntities(subject, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    private CharSequence parseComment(String commentRaw) {
        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.replace("<wbr>", "");

            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();

            for (Node node : nodes) {
                CharSequence nodeParsed = parseNode(node);
                if (nodeParsed != null) {
                    total = TextUtils.concat(total, nodeParsed);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return total;
    }

    private CharSequence parseNode(Node node) {
        if (node instanceof TextNode) {
            String text = ((TextNode) node).text();
            SpannableString spannable = new SpannableString(text);

            detectLinks(text, spannable);

            return spannable;
        } else {
            switch (node.nodeName()) {
                case "br": {
                    return "\n";
                }
                case "span": {
                    Element span = (Element) node;

                    SpannableString quote = new SpannableString(span.text());

                    Set<String> classes = span.classNames();
                    if (classes.contains("deadlink")) {
                        quote.setSpan(new ForegroundColorSpan(ThemeHelper.getInstance().getQuoteColor()), 0, quote.length(), 0);
                        quote.setSpan(new StrikethroughSpan(), 0, quote.length(), 0);
                    } else {
                        quote.setSpan(new ForegroundColorSpan(ThemeHelper.getInstance().getInlineQuoteColor()), 0, quote.length(), 0);
                        detectLinks(span.text(), quote);
                    }

                    return quote;
                }
                case "a": {
                    CharSequence anchor = parseAnchor((Element) node);
                    if (anchor != null) {
                        return anchor;
                    } else {
                        return ((Element) node).text();
                    }
                }
                case "s": {
                    Element spoiler = (Element) node;

                    SpannableString link = new SpannableString(spoiler.text());

                    PostLinkable pl = new PostLinkable(this, spoiler.text(), spoiler.text(), Type.SPOILER);
                    link.setSpan(pl, 0, link.length(), 0);
                    linkables.add(pl);

                    return link;
                }
                case "pre": {
                    Element pre = (Element) node;

                    Set<String> classes = pre.classNames();
                    if (classes.contains("prettyprint")) {
                        String text = getNodeText(pre);
                        SpannableString monospace = new SpannableString(text);
                        monospace.setSpan(new TypefaceSpan("monospace"), 0, monospace.length(), 0);
                        monospace.setSpan(new AbsoluteSizeSpan(ThemeHelper.getInstance().getCodeTagSize()), 0, monospace.length(), 0);
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

    private CharSequence parseAnchor(Element anchor) {
        String href = anchor.attr("href");
        Set<String> classes = anchor.classNames();

        Type t = null;
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
                        } catch (NumberFormatException e) {
                        }
                    }
                }

                if (threadLink != null) {
                    t = Type.THREAD;
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
                    } catch (NumberFormatException e) {
                    }
                }

                if (id >= 0) {
                    t = Type.QUOTE;
                    key = anchor.text();
                    value = id;
                    repliesTo.add(id);

                    // Append OP when its a reply to OP
                    if (id == resto) {
                        key += " (OP)";
                    }

                    // Append You when it's a reply to an saved reply
                    if (ChanApplication.getDatabaseManager().isSavedReply(board, id)) {
                        key += " (You)";
                    }
                }
            }
        } else {
            // normal link
            t = Type.LINK;
            key = anchor.text();
            value = href;
        }

        if (t != null && key != null && value != null) {
            SpannableString link = new SpannableString(key);
            PostLinkable pl = new PostLinkable(this, key, value, t);
            link.setSpan(pl, 0, link.length(), 0);
            linkables.add(pl);

            return link;
        } else {
            return null;
        }
    }

    private void detectLinks(String text, SpannableString spannable) {
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

            PostLinkable pl = new PostLinkable(this, linkString, linkString, PostLinkable.Type.LINK);
            spannable.setSpan(pl, startPos, endPos, 0);
            linkables.add(pl);

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
