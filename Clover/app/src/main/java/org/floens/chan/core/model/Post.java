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
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.PostLinkable.Type;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.ThemeHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

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
                String nodeName = node.nodeName();

                if (node instanceof TextNode) {
                    String text = ((TextNode) node).text();
                    SpannableString spannable = new SpannableString(text);

                    detectLinks(text, spannable);

                    total = TextUtils.concat(total, spannable);
                } else {
                    switch (nodeName) {
                        case "br": {
                            total = TextUtils.concat(total, "\n");
                            break;
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

                            total = TextUtils.concat(total, quote);
                            break;
                        }
                        case "a": {
                            Element anchor = (Element) node;

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

                                total = TextUtils.concat(total, link);
                            }
                            break;
                        }
                        case "s": {
                            Element spoiler = (Element) node;

                            SpannableString link = new SpannableString(spoiler.text());

                            PostLinkable pl = new PostLinkable(this, spoiler.text(), spoiler.text(), Type.SPOILER);
                            link.setSpan(pl, 0, link.length(), 0);
                            linkables.add(pl);

                            total = TextUtils.concat(total, link);
                            break;
                        }
                        default: {
                            // Unknown tag, add the inner part
                            if (node instanceof Element) {
                                total = TextUtils.concat(total, ((Element) node).text());
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return total;
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
}
