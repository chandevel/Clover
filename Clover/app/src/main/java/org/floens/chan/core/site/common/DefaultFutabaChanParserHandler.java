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
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.jsoup.nodes.Element;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultFutabaChanParserHandler implements FutabaChanParserHandler {
    private static final Pattern COLOR_PATTERN = Pattern.compile("color:#([0-9a-fA-F]*)");

    @Override
    public CharSequence handleParagraph(FutabaChanParser parser, Theme theme, Post.Builder post, CharSequence text, Element element) {
        return text;
    }

    @Override
    public CharSequence handleSpan(FutabaChanParser parser, Theme theme, Post.Builder post, Element span) {
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

                // private static final Pattern COLOR_PATTERN = Pattern.compile("color:#([0-9a-fA-F]*)");
                Matcher matcher = COLOR_PATTERN.matcher(style);

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
            parser.detectLinks(theme, post, span.text(), quote);
        }

        return quote;
    }

    @Override
    public Link getLink(FutabaChanParser parser, Theme theme, Post.Builder post, Element anchor) {
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
                    key = anchor.text();
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
