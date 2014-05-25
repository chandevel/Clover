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

import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.PostLinkable.Type;
import org.floens.chan.ui.view.PostView;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * This post replies to the these ids
     */
    public List<Integer> repliesTo = new ArrayList<Integer>();

    /**
     * These ids replied to this post
     */
    public List<Integer> repliesFrom = new ArrayList<Integer>();

    public final ArrayList<PostLinkable> linkables = new ArrayList<PostLinkable>();

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

        if (isOP && replies < 0)
            return false;

        if (ext != null) {
            hasImage = true;
        }

        if (hasImage) {
            if (filename == null || tim == null || ext == null || imageWidth <= 0 || imageHeight <= 0)
                return false;

            thumbnailUrl = ChanUrls.getThumbnailUrl(board, tim);
            imageUrl = ChanUrls.getImageUrl(board, tim, ext);
        }

        if (rawComment != null) {
            comment = parseComment(rawComment, loadable.simpleMode);
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

    private CharSequence parseComment(String commentRaw, boolean simpleMode) {
        if (simpleMode)
            return "";

        CharSequence total = new SpannableString("");

        try {
            String comment = commentRaw.replace("<wbr>", "");

            Document document = Jsoup.parseBodyFragment(comment);

            List<Node> nodes = document.body().childNodes();

            for (Node node : nodes) {
                String nodeName = node.nodeName();

                if (node instanceof TextNode) {
                    String text = ((TextNode) node).text();

                    // Find url's in the text node
                    if (text.contains("://")) {
                        String[] parts = text.split("\\s");

                        for (String item : parts) {
                            if (item.contains("://")) {
                                try {
                                    URL url = new URL(item);

                                    SpannableString link = new SpannableString(url.toString());

                                    PostLinkable pl = new PostLinkable(this, item, item, PostLinkable.Type.LINK);
                                    link.setSpan(pl, 0, link.length(), 0);
                                    linkables.add(pl);

                                    total = TextUtils.concat(total, link, " ");
                                } catch (Exception e) {
                                    total = TextUtils.concat(total, item, " ");
                                }
                            } else {
                                total = TextUtils.concat(total, item, " ");
                            }
                        }
                    } else {
                        total = TextUtils.concat(total, text);
                    }
                } else if (nodeName.equals("br")) {
                    total = TextUtils.concat(total, "\n");
                } else if (nodeName.equals("span")) {
                    Element span = (Element) node;

                    SpannableString quote = new SpannableString(span.text());
                    quote.setSpan(new ForegroundColorSpan(Color.argb(255, 120, 153, 34)), 0, quote.length(), 0);

                    total = TextUtils.concat(total, quote);
                } else if (nodeName.equals("a")) {
                    Element anchor = (Element) node;

                    SpannableString link = new SpannableString(anchor.text());

                    Type t = anchor.text().contains("://") ? Type.LINK : Type.QUOTE;
                    PostLinkable pl = new PostLinkable(this, anchor.text(), anchor.attr("href"), t);
                    link.setSpan(pl, 0, link.length(), 0);
                    linkables.add(pl);

                    if (t == Type.QUOTE) {
                        try {
                            // Get post id
                            String[] splitted = anchor.attr("href").split("#p");
                            if (splitted.length == 2) {
                                int id = Integer.parseInt(splitted[1]);
                                repliesTo.add(id);
                            }
                        } catch (NumberFormatException e) {
                        }
                    }

                    total = TextUtils.concat(total, link);
                } else if (nodeName.equals("s")) {
                    Element spoiler = (Element) node;

                    SpannableString link = new SpannableString(spoiler.text());

                    PostLinkable pl = new PostLinkable(this, spoiler.text(), spoiler.text(), Type.SPOILER);
                    link.setSpan(pl, 0, link.length(), 0);
                    linkables.add(pl);

                    total = TextUtils.concat(total, link);
                } else {
                    // Unknown tag, add the inner part
                    if (node instanceof Element) {
                        total = TextUtils.concat(total, ((Element) node).text());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return total;
    }
}
