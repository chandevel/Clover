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
package org.floens.chan.core.site.parser;

import android.graphics.Typeface;
import android.support.annotation.AnyThread;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.ui.span.AbsoluteSizeSpanHashed;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.floens.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParser {
    private static final String SAVED_REPLY_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    private static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right

    private Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private Pattern quotePattern = Pattern.compile(".*#p(\\d+)");
    private Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]*)");

    public void setQuotePattern(Pattern quotePattern) {
        this.quotePattern = quotePattern;
    }

    public void setFullQuotePattern(Pattern fullQuotePattern) {
        this.fullQuotePattern = fullQuotePattern;
    }

    public CharSequence handleTag(PostParser.Callback callback,
                                  Theme theme,
                                  Post.Builder post,
                                  String tag,
                                  CharSequence text,
                                  Element element) {
        switch (tag) {
            case "br":
                return "\n";
            case "span":
                return handleSpan(theme, post, text, element);
            case "p":
                return appendBreakIfNotLastSibling(
                        handleParagraph(theme, post, text, element), element);
            case "table":
                return handleTable(theme, post, text, element);
            case "strong":
                return handleStrong(theme, post, text, element);
            case "a":
                return handleAnchor(theme, post, text, element, callback);
            case "s":
                return handleStrike(theme, post, text, element);
            case "pre":
                return handlePre(theme, post, text, element);
            default:
                // Unknown tag, return the text;
                return text;
        }
    }

    private CharSequence appendBreakIfNotLastSibling(CharSequence text, Element element) {
        if (element.nextSibling() != null) {
            return TextUtils.concat(text, "\n");
        } else {
            return text;
        }
    }

    private CharSequence handleAnchor(Theme theme,
                                      Post.Builder post,
                                      CharSequence text,
                                      Element anchor,
                                      PostParser.Callback callback) {
        CommentParser.Link handlerLink = matchAnchor(post, text, anchor, callback);

        if (handlerLink != null) {
            if (handlerLink.type == PostLinkable.Type.THREAD) {
                handlerLink.key = TextUtils.concat(handlerLink.key, EXTERN_THREAD_LINK_SUFFIX);
            }

            if (handlerLink.type == PostLinkable.Type.QUOTE) {
                int postNo = (int) handlerLink.value;
                post.addReplyTo(postNo);

                // Append (OP) when it's a reply to OP
                if (postNo == post.opId) {
                    handlerLink.key = TextUtils.concat(handlerLink.key, OP_REPLY_SUFFIX);
                }

                // Append (You) when it's a reply to an saved reply
                if (callback.isSaved(postNo)) {
                    handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_SUFFIX);
                }
            }

            SpannableString res = new SpannableString(handlerLink.key);
            PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
            res.setSpan(pl, 0, res.length(), 0);
            post.addLinkable(pl);

            return res;
        } else {
            return null;
        }
    }

    public CharSequence handleSpan(Theme theme, Post.Builder post, CharSequence text, Element span) {
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
        } else if (classes.contains("spoiler")) {
            PostLinkable pl = new PostLinkable(theme, span.text(), span.text(), PostLinkable.Type.SPOILER);
            post.addLinkable(pl);
            return span(span.text(), pl);
        } else if (classes.contains("abbr")) {
            return null;
        } else {
            quote = new SpannableString(span.text());
            quote.setSpan(new ForegroundColorSpanHashed(theme.inlineQuoteColor), 0, quote.length(), 0);
            CommentParserHelper.detectLinks(theme, post, span.text(), quote);
        }

        return quote;
    }

    public CharSequence handleParagraph(Theme theme, Post.Builder post, CharSequence text, Element span) {
        return text;
    }

    public CharSequence handleTable(Theme theme, Post.Builder post, CharSequence text, Element table) {
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

                    if (j < tableDatas.size() - 1) parts.add(": ");
                }

                if (i < tableRows.size() - 1) parts.add("\n");
            }
        }

        // Overrides the text (possibly) parsed by child nodes.
        return span(TextUtils.concat(parts.toArray(new CharSequence[parts.size()])),
                new ForegroundColorSpanHashed(theme.inlineQuoteColor),
                new AbsoluteSizeSpanHashed(sp(12f)));
    }

    public CharSequence handleStrong(Theme theme, Post.Builder post, CharSequence text, Element strong) {
        return span(text,
                new ForegroundColorSpanHashed(theme.quoteColor),
                new StyleSpan(Typeface.BOLD));
    }

    public CharSequence handlePre(Theme theme, Post.Builder post, CharSequence text, Element pre) {
        Set<String> classes = pre.classNames();
        if (classes.contains("prettyprint")) {
//            String linebreakText = CommentParserHelper.getNodeTextPreservingLineBreaks(pre);
            return span(text,
                    new TypefaceSpan("monospace"),
                    new AbsoluteSizeSpanHashed(sp(12f)));
        } else {
            return pre.text();
        }
    }

    public CharSequence handleStrike(Theme theme, Post.Builder post, CharSequence text, Element strike) {
        PostLinkable pl = new PostLinkable(theme, text.toString(), text, PostLinkable.Type.SPOILER);
        post.addLinkable(pl);

        return span(text, pl);
    }

    public Link matchAnchor(Post.Builder post, CharSequence text, Element anchor, PostParser.Callback callback) {
        String href = anchor.attr("href");

        PostLinkable.Type t;
        Object value;

        Matcher externalMatcher = fullQuotePattern.matcher(href);
        if (externalMatcher.matches()) {
            String board = externalMatcher.group(1);
            int threadId = Integer.parseInt(externalMatcher.group(2));
            int postId = Integer.parseInt(externalMatcher.group(3));

            if (board.equals(post.board.code) && callback.isInternal(postId)) {
                t = PostLinkable.Type.QUOTE;
                value = postId;
            } else {
                t = PostLinkable.Type.THREAD;
                value = new PostLinkable.ThreadLink(board, threadId, postId);
            }
        } else {
            Matcher quoteMatcher = quotePattern.matcher(href);
            if (quoteMatcher.matches()) {
                t = PostLinkable.Type.QUOTE;
                value = Integer.parseInt(quoteMatcher.group(1));
            } else {
                // normal link
                t = PostLinkable.Type.LINK;
                value = href;
            }
        }

        Link link = new Link();
        link.type = t;
        link.key = text;
        link.value = value;
        return link;
    }

    public SpannableString span(CharSequence text, Object... additionalSpans) {
        SpannableString result = new SpannableString(text);
        int l = result.length();

        if (additionalSpans != null && additionalSpans.length > 0) {
            for (Object additionalSpan : additionalSpans) {
                if (additionalSpan != null) {
                    result.setSpan(additionalSpan, 0, l, 0);
                }
            }
        }

        return result;
    }

    public class Link {
        public PostLinkable.Type type;
        public CharSequence key;
        public Object value;
    }
}
