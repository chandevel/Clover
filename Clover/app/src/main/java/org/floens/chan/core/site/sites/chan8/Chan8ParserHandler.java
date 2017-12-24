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
package org.floens.chan.core.site.sites.chan8;

import android.text.SpannableString;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.site.common.ChanParserHelper;
import org.floens.chan.core.site.common.DefaultFutabaChanParserHandler;
import org.floens.chan.core.site.common.FutabaChanParser;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.jsoup.nodes.Element;

import java.util.Set;

public class Chan8ParserHandler extends DefaultFutabaChanParserHandler {
    @Override
    public CharSequence handleParagraph(FutabaChanParser parser, Theme theme, Post.Builder post, CharSequence text, Element element) {
        if (element.hasClass("quote")) {
            SpannableString quote = new SpannableString(text);
            quote.setSpan(new ForegroundColorSpanHashed(theme.inlineQuoteColor), 0, quote.length(), 0);
            ChanParserHelper.detectLinks(theme, post, quote.toString(), quote);
            return quote;
        } else {
            return text;
        }
    }

    @Override
    public CharSequence handleSpan(FutabaChanParser parser, Theme theme, Post.Builder post, Element span) {
        SpannableString quote;

        Set<String> classes = span.classNames();
        if (classes.contains("abbr")) {
            return null;
        } else if (classes.contains("spoiler")) {
            quote = new SpannableString(span.text());
            PostLinkable pl = new PostLinkable(theme, span.text(), span.text(), PostLinkable.Type.SPOILER);
            quote.setSpan(pl, 0, quote.length(), 0);
            post.addLinkable(pl);
        } else {
            quote = new SpannableString(span.text());
            quote.setSpan(new ForegroundColorSpanHashed(theme.inlineQuoteColor), 0, quote.length(), 0);
            ChanParserHelper.detectLinks(theme, post, span.text(), quote);
        }

        return quote;
    }

    @Override
    public Link handleAnchor(FutabaChanParser parser, Theme theme, Post.Builder post, Element anchor) {
        String href = anchor.attr("href");

        PostLinkable.Type t = null;
        String key = null;
        Object value = null;
        if (href.startsWith("/")) {
            if (!href.startsWith("/" + post.board.code + "/res/")) {
                // link to another thread
                PostLinkable.ThreadLink threadLink = null;

                String[] slashSplit = href.split("/");
                if (slashSplit.length == 4) {
                    String board = slashSplit[1];
                    String nums = slashSplit[3];
                    String[] numsSplitted = nums.split("#");
                    if (numsSplitted.length == 2) {
                        try {
                            int tId = Integer.parseInt(numsSplitted[0].replace(".html", ""));
                            int pId = Integer.parseInt(numsSplitted[1]);
                            threadLink = new PostLinkable.ThreadLink(board, tId, pId);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                if (threadLink != null) {
                    t = PostLinkable.Type.THREAD;
                    key = anchor.text();
                    value = threadLink;
                }
            } else {
                // normal quote
                int id = -1;

                String[] splitted = href.split("#");
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
                }
            }
        } else {
            // normal link
            t = PostLinkable.Type.LINK;
            key = anchor.text();
            value = href;
        }

        if (t != null && key != null && value != null) {
            Link link = new Link();
            link.type = t;
            link.key = key;
            link.value = value;
            return link;
        } else {
            return null;
        }
    }
}
