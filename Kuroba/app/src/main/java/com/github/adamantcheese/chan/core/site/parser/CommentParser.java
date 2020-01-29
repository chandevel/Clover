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
package com.github.adamantcheese.chan.core.site.parser;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.tagRule;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParser {
    private static final String SAVED_REPLY_SELF_SUFFIX = " (Me)";
    private static final String SAVED_REPLY_OTHER_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    private static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right

    private Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p(\\d+)");
    private Pattern quotePattern = Pattern.compile(".*#p(\\d+)");
    private Pattern boardLinkPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/");
    private Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    private Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");
    private Pattern colorPattern = Pattern.compile("color:#([0-9a-fA-F]+)");

    private Map<String, List<StyleRule>> rules = new HashMap<>();

    public CommentParser() {
        // Required tags.
        rule(tagRule("p"));
        rule(tagRule("div"));
        rule(tagRule("br").just("\n"));
    }

    public void addDefaultRules() {
        rule(tagRule("a").action(this::handleAnchor));

        rule(tagRule("span").cssClass("deadlink")
                .foregroundColor(StyleRule.ForegroundColor.QUOTE)
                .strikeThrough()
                .action(this::handleDead));
        rule(tagRule("span").cssClass("spoiler").link(PostLinkable.Type.SPOILER));
        rule(tagRule("span").cssClass("fortune").action(this::handleFortune));
        rule(tagRule("span").cssClass("abbr").nullify());
        rule(tagRule("span").foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE).linkify());

        rule(tagRule("table").action(this::handleTable));

        rule(tagRule("s").link(PostLinkable.Type.SPOILER));

        rule(tagRule("strong").bold());
        rule(tagRule("strong-red;").bold().foregroundColor(StyleRule.ForegroundColor.RED));
        rule(tagRule("b").bold());

        rule(tagRule("i").italic());
        rule(tagRule("em").italic());

        rule(tagRule("pre").cssClass("prettyprint")
                .monospace()
                .size(sp(12f))
                .backgroundColor(StyleRule.BackgroundColor.CODE));
    }

    public void rule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        list.add(rule);
    }

    public void setQuotePattern(Pattern quotePattern) {
        this.quotePattern = quotePattern;
    }

    public void setFullQuotePattern(Pattern fullQuotePattern) {
        this.fullQuotePattern = fullQuotePattern;
    }

    public CharSequence handleTag(
            PostParser.Callback callback, Theme theme, Post.Builder post, String tag, CharSequence text, Element element
    ) {

        List<StyleRule> rules = this.rules.get(tag);
        if (rules != null) {
            for (int i = 0; i < 2; i++) {
                boolean highPriority = i == 0;
                for (StyleRule rule : rules) {
                    if (rule.highPriority() == highPriority && rule.applies(element)) {
                        return rule.apply(theme, callback, post, text, element);
                    }
                }
            }
        }

        // Unknown tag, return the text;
        return text;
    }

    private CharSequence handleAnchor(
            Theme theme, PostParser.Callback callback, Post.Builder post, CharSequence text, Element anchor
    ) {
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

                // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
                if (callback.isSaved(postNo)) {
                    if (post.isSavedReply) {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_SELF_SUFFIX);
                    } else {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_OTHER_SUFFIX);
                    }
                }
            }

            SpannableString res = new SpannableString(handlerLink.key);
            PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
            res.setSpan(pl, 0, res.length(), (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);
            post.addLinkable(pl);

            return res;
        } else {
            return null;
        }
    }

    private CharSequence handleFortune(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element span
    ) {
        // html looks like <span class="fortune" style="color:#0893e1"><br><br><b>Your fortune:</b>
        String style = span.attr("style");
        if (!TextUtils.isEmpty(style)) {
            style = style.replace(" ", "");

            Matcher matcher = colorPattern.matcher(style);
            if (matcher.find()) {
                int hexColor = Integer.parseInt(matcher.group(1), 16);
                if (hexColor >= 0 && hexColor <= 0xffffff) {
                    text = span(text,
                            new ForegroundColorSpanHashed(0xff000000 + hexColor),
                            new StyleSpan(Typeface.BOLD)
                    );
                }
            }
        }

        return text;
    }

    public CharSequence handleTable(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element table
    ) {
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
        return span(TextUtils.concat(parts.toArray(new CharSequence[0])),
                new ForegroundColorSpanHashed(theme.inlineQuoteColor),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public CharSequence handleDead(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element deadlink
    ) {
        //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
        try {
            int postNo = Integer.parseInt(deadlink.text().substring(2));
            List<ArchivesLayout.PairForAdapter> boards = instance(ArchivesManager.class).domainsForBoard(builder.board);
            if (!boards.isEmpty() && builder.op) {
                //only allow same board deadlinks to be parsed in the OP, as they are likely previous thread links
                //if a deadlink appears in a regular post that is likely to be a dead post link, we are unable to link to an archive
                //as there are no URLs that directly will allow you to link to a post and be redirected to the right thread
                Site site = builder.board.site;
                String link =
                        site.resolvable().desktopUrl(Loadable.forThread(site, builder.board, postNo, ""), builder.id);
                link = link.replace("https://boards.4chan.org/", "https://" + boards.get(0).second + "/");
                PostLinkable newLinkable = new PostLinkable(theme, link, link, PostLinkable.Type.LINK);
                text = span(text, newLinkable);
                builder.addLinkable(newLinkable);
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    public Link matchAnchor(Post.Builder post, CharSequence text, Element anchor, PostParser.Callback callback) {
        String href = anchor.attr("href");
        //gets us something like /board/ or /thread/postno#quoteno
        //hacky fix for 4chan having two domains but the same API
        if (href.matches("//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)")) {
            href = href.substring(2);
            href = href.substring(href.indexOf('/'));
        }

        PostLinkable.Type t;
        Object value;

        Matcher externalMatcher = fullQuotePattern.matcher(href);
        if (externalMatcher.matches()) {
            String board = externalMatcher.group(1);
            int threadId = Integer.parseInt(externalMatcher.group(2));
            int postId = Integer.parseInt(externalMatcher.group(3));

            if (board.equals(post.board.code) && callback.isInternal(postId)) {
                //link to post in same thread with post number (>>post)
                t = PostLinkable.Type.QUOTE;
                value = postId;
            } else {
                //link to post not in same thread with post number (>>post or >>>/board/post)
                t = PostLinkable.Type.THREAD;
                value = new ThreadLink(board, threadId, postId);
            }
        } else {
            Matcher quoteMatcher = quotePattern.matcher(href);
            if (quoteMatcher.matches()) {
                //link to post backup???
                t = PostLinkable.Type.QUOTE;
                value = Integer.parseInt(quoteMatcher.group(1));
            } else {
                Matcher boardLinkMatcher = boardLinkPattern.matcher(href);
                Matcher boardLinkMatcher8Chan = boardLinkPattern8Chan.matcher(href);
                Matcher boardSearchMatcher = boardSearchPattern.matcher(href);
                if (boardLinkMatcher.matches() || boardLinkMatcher8Chan.matches()) {
                    //board link
                    t = PostLinkable.Type.BOARD;
                    value = boardLinkMatcher.matches() ? boardLinkMatcher.group(1) : boardLinkMatcher8Chan.group(1);
                } else if (boardSearchMatcher.matches()) {
                    //search link
                    String board = boardSearchMatcher.group(1);
                    String search;
                    try {
                        search = URLDecoder.decode(boardSearchMatcher.group(2), "US-ASCII");
                    } catch (UnsupportedEncodingException e) {
                        search = boardSearchMatcher.group(2);
                    }
                    t = PostLinkable.Type.SEARCH;
                    value = new SearchLink(board, search);
                } else {
                    //normal link
                    t = PostLinkable.Type.LINK;
                    value = href;
                }
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

    public static class ThreadLink {
        public String board;
        public int threadId;
        public int postId;

        public ThreadLink(String board, int threadId, int postId) {
            this.board = board;
            this.threadId = threadId;
            this.postId = postId;
        }
    }

    public static class SearchLink {
        public String board;
        public String search;

        public SearchLink(String board, String search) {
            this.board = board;
            this.search = search;
        }
    }
}
