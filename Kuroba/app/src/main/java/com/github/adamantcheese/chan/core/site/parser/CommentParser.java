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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.JsonReader;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.PostLinkable.Type;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive.ArchiveEndpoints;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive.ArchiveSiteUrlHandler;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.CustomTypefaceSpan;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.common.io.Files;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.site.parser.StyleRule.tagRule;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParser {
    private static final String SAVED_REPLY_SELF_SUFFIX = " (Me)";
    private static final String SAVED_REPLY_OTHER_SUFFIX = " (You)";
    private static final String OP_REPLY_SUFFIX = " (OP)";
    private static final String EXTERN_THREAD_LINK_SUFFIX = " \u2192"; // arrow to the right
    public static final String EXIF_INFO_STRING = "[EXIF data available. Click here to view.]";

    private Pattern fullQuotePattern = Pattern.compile("/(\\w+)/\\w+/(\\d+)#p?(\\d+)");
    private Pattern quotePattern = Pattern.compile(".*#p?(\\d+)");

    // A pattern matching any board links
    private final Pattern boardLinkPattern =
            Pattern.compile("(?:https?:?)?(?://boards\\.4chan.*?\\.org)?/(.*?)/(?:catalog)?");
    //alternate for some sites (formerly 8chan)
    private final Pattern boardLinkPattern8Chan = Pattern.compile("/(.*?)/index.html");
    // A pattern matching any board search links
    private final Pattern boardSearchPattern = Pattern.compile("//boards\\.4chan.*?\\.org/(.*?)/catalog#s=(.*)");

    // The list of rules for this parser, mapping an HTML tag to a list of StyleRules that need to be applied for that tag
    private final Map<String, List<StyleRule>> rules = new HashMap<>();

    private static Typeface submona;

    public CommentParser() {
        // Required tags.
        rule(tagRule("p"));
        rule(tagRule("div"));
        rule(tagRule("br").just("\n"));
    }

    public CommentParser addDefaultRules() {
        rule(tagRule("a").action(this::handleAnchor));

        rule(tagRule("span").cssClass("deadlink")
                .foregroundColor(R.attr.post_quote_color, true)
                .strikeThrough()
                .action(this::handleDead));
        rule(tagRule("span").cssClass("spoiler").spoiler());
        rule(tagRule("span").cssClass("fortune").bold().cssStyleInFront());
        rule(tagRule("span").cssClass("abbr").nullify());
        rule(tagRule("span").cssClass("quote").foregroundColor(R.attr.post_inline_quote_color, true));
        rule(tagRule("span").cssClass("sjis").action(this::handleSJIS));
        rule(tagRule("span")); // this allows inline styled elements to be processed

        rule(tagRule("table").action(this::handleTable));

        rule(tagRule("s").spoiler());

        rule(tagRule("strong").bold());
        rule(tagRule("b").bold());

        rule(tagRule("strike").strikeThrough());

        rule(tagRule("i").italic());
        rule(tagRule("em").italic());

        rule(tagRule("u").underline());

        rule(tagRule("font").applyFontRules());

        rule(tagRule("pre").cssClass("prettyprint").monospace().code().trimEndWhitespace().size(sp(12f)));

        // replaces img tags with an attached image, and any alt-text will become a spoilered text item
        rule(tagRule("img").action((theme, callback, post, text, element) -> {
            try {
                SpannableString ret = new SpannableString(text);
                if (element.hasAttr("alt")) {
                    String alt = element.attr("alt");
                    if (!alt.isEmpty()) {
                        ret = new SpannableString(alt + " ");
                        ret.setSpan(new PostLinkable(theme, alt, alt, Type.SPOILER),
                                0,
                                alt.length(),
                                (1000 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
                        );
                    }
                }
                HttpUrl src = HttpUrl.get(element.attr("src"));
                PostImage i = new PostImage.Builder().imageUrl(src)
                        .thumbnailUrl(src)
                        .spoilerThumbnailUrl(src)
                        .filename(Files.getNameWithoutExtension(src.toString()))
                        .extension(Files.getFileExtension(src.toString()))
                        .build();
                if (post.images.size() < 5 && !post.images.contains(i)) {
                    post.images(Collections.singletonList(i));
                }
                return ret;
            } catch (Exception e) {
                return text;
            }
        }));

        // replaces iframes with the associated src url text
        rule(tagRule("iframe").action((theme, callback, post, text, element) -> element.attr("src")));
        return this;
    }

    public void rule(StyleRule rule) {
        List<StyleRule> list = rules.get(rule.tag());
        if (list == null) {
            list = new ArrayList<>(3);
            rules.put(rule.tag(), list);
        }

        list.add(rule);
    }

    /**
     * @param quotePattern The quote pattern to use for quotes within a thread, matching the href of an 'a' element<br>
     *                     Should contain a single matching group that resolves to the post number for the quote
     */
    public void setQuotePattern(Pattern quotePattern) {
        this.quotePattern = quotePattern;
    }

    /**
     * @param fullQuotePattern The quote pattern to use for quotes linking outside a thread, matching the href of an 'a' element<br>
     *                         Should contain three matching groups that resolve to the board code, op number, and post number
     */
    public void setFullQuotePattern(Pattern fullQuotePattern) {
        this.fullQuotePattern = fullQuotePattern;
    }

    public String createQuoteElementString(Post.Builder post) {
        return "<a href=\"/" + post.board.code + "/thread/" + post.opId + "#p$1\">&gt;&gt;$1</a>";
    }

    public CharSequence handleTag(
            PostParser.Callback callback,
            @NonNull Theme theme,
            Post.Builder post,
            String tag,
            CharSequence text,
            Element element
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
            @NonNull Theme theme, PostParser.Callback callback, Post.Builder post, CharSequence text, Element anchor
    ) {
        Link handlerLink = null;
        try {
            handlerLink = matchAnchor(post, text, anchor, callback);
        } catch (Exception e) {
            Logger.w(this, "Failed to parse an element, leaving as plain text.");
        }
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();

        if (handlerLink != null) {
            addReply(theme, callback, post, handlerLink, spannableStringBuilder);
        } else {
            spannableStringBuilder.append(text);
        }

        return spannableStringBuilder.length() > 0 ? spannableStringBuilder : null;
    }

    private void addReply(
            @NonNull Theme theme,
            PostParser.Callback callback,
            Post.Builder post,
            Link handlerLink,
            SpannableStringBuilder spannableStringBuilder
    ) {
        if (handlerLink.type == Type.THREAD && !handlerLink.key.toString().contains(EXTERN_THREAD_LINK_SUFFIX)) {
            handlerLink.key = TextUtils.concat(handlerLink.key, EXTERN_THREAD_LINK_SUFFIX);
        }

        if (handlerLink.type == Type.ARCHIVE && (
                (handlerLink.value instanceof ThreadLink && ((ThreadLink) handlerLink.value).postId == -1)
                        || handlerLink.value instanceof ResolveLink) && !handlerLink.key.toString()
                .contains(EXTERN_THREAD_LINK_SUFFIX)) {
            handlerLink.key = TextUtils.concat(handlerLink.key, EXTERN_THREAD_LINK_SUFFIX);
        }

        if (handlerLink.type == Type.QUOTE) {
            int postNo = (int) handlerLink.value;
            post.repliesTo(Collections.singleton(postNo));

            // Append (OP) when it's a reply to OP
            if (postNo == post.opId && !handlerLink.key.toString().contains(OP_REPLY_SUFFIX)) {
                handlerLink.key = TextUtils.concat(handlerLink.key, OP_REPLY_SUFFIX);
            }

            // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
            if (callback.isSaved(postNo)) {
                if (post.isSavedReply) {
                    if (!handlerLink.key.toString().contains(SAVED_REPLY_SELF_SUFFIX)) {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_SELF_SUFFIX);
                    }
                } else {
                    if (!handlerLink.key.toString().contains(SAVED_REPLY_OTHER_SUFFIX)) {
                        handlerLink.key = TextUtils.concat(handlerLink.key, SAVED_REPLY_OTHER_SUFFIX);
                    }
                }
            }
        }

        SpannableString res = new SpannableString(handlerLink.key);
        PostLinkable pl = new PostLinkable(theme, handlerLink.key, handlerLink.value, handlerLink.type);
        res.setSpan(pl, 0, res.length(), (250 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);

        spannableStringBuilder.append(res);
    }

    // This is used on /p/ for exif data.
    public CharSequence handleTable(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element table
    ) {
        SpannableStringBuilder parts = new SpannableStringBuilder();
        Elements tableRows = table.getElementsByTag("tr");
        for (int i = 0; i < tableRows.size(); i++) {
            Element tableRow = tableRows.get(i);
            if (tableRow.text().length() > 0) {
                Elements tableDatas = tableRow.getElementsByTag("td");
                for (int j = 0; j < tableDatas.size(); j++) {
                    Element tableData = tableDatas.get(j);

                    SpannableString tableDataPart = new SpannableString(tableData.text());
                    if (tableData.getElementsByTag("b").size() > 0) {
                        tableDataPart = span(tableDataPart, new StyleSpan(Typeface.BOLD), new UnderlineSpan());
                    }

                    parts.append(tableDataPart);

                    if (j < tableDatas.size() - 1) parts.append(": ");
                }

                if (i < tableRows.size() - 1) parts.append("\n");
            }
        }

        // Overrides the text (possibly) parsed by child nodes.
        return span(EXIF_INFO_STRING,
                new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        AlertDialog dialog = getDefaultAlertBuilder(widget.getContext()).setMessage(parts)
                                .setPositiveButton(R.string.ok, null)
                                .create();
                        dialog.setCanceledOnTouchOutside(true);
                        dialog.show();
                    }
                },
                new ForegroundColorSpanHashed(getAttrColor(theme.resValue, R.attr.post_inline_quote_color)),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public CharSequence handleSJIS(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element deadlink
    ) {
        if (submona == null) {
            submona = Typeface.createFromAsset(getAppContext().getAssets(), "font/submona.ttf");
        }
        SpannableStringBuilder sjisArt = new SpannableStringBuilder(text);
        sjisArt.setSpan(new CustomTypefaceSpan("", submona), 0, sjisArt.length(), 0);
        return span("[SJIS art available. Click here to view.]",
                new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        TextView sjisView = new TextView(widget.getContext());
                        sjisView.setMovementMethod(new ScrollingMovementMethod());
                        sjisView.setHorizontallyScrolling(true);
                        sjisView.setPadding(dp(16), dp(16), dp(16), dp(16));
                        sjisView.setText(sjisArt);
                        AlertDialog dialog = getDefaultAlertBuilder(widget.getContext()).setView(sjisView)
                                .setPositiveButton(R.string.close, null)
                                .create();
                        dialog.setCanceledOnTouchOutside(true);
                        dialog.show();
                    }
                },
                new ForegroundColorSpanHashed(getAttrColor(theme.resValue, R.attr.post_inline_quote_color)),
                new AbsoluteSizeSpanHashed(sp(12f))
        );
    }

    public CharSequence handleDead(
            Theme theme, PostParser.Callback callback, Post.Builder builder, CharSequence text, Element deadlink
    ) {
        //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
        try {
            if (!(builder.board.site instanceof Chan4)) return text; //4chan only
            int postNo = Integer.parseInt(deadlink.text().substring(2));
            List<ExternalSiteArchive> boards = ArchivesManager.getInstance().archivesForBoard(builder.board);
            if (!boards.isEmpty()) {
                PostLinkable newLinkable = new PostLinkable(theme,
                        text,
                        // if the deadlink is in an external archive, set a resolve link
                        // if the deadlink is in any other site, we don't have enough info to properly link to stuff, so
                        // we assume that deadlinks in an OP are previous threads
                        // and any deadlinks in other posts are deleted posts in the same thread
                        builder.board.site instanceof ExternalSiteArchive
                                ? new ResolveLink(builder.board.site,
                                builder.board.code,
                                postNo
                        )
                                : new ThreadLink(builder.board.code,
                                        builder.op ? postNo : builder.opId,
                                        builder.op ? -1 : postNo
                                ),
                        Type.ARCHIVE
                );
                text = span(text, newLinkable);
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    public Link matchAnchor(Post.Builder post, CharSequence text, Element anchor, PostParser.Callback callback)
            throws Exception {
        String href = anchor.attr("href");
        //gets us something like /board/ or /thread/postno#quoteno
        //hacky fix for 4chan having two domains but the same API
        if (href.matches("//boards\\.4chan.*?\\.org/(.*?)/thread/(\\d*?)#p(\\d*)")) {
            href = href.substring(2);
            href = href.substring(href.indexOf('/'));
        }

        Type t;
        Object value;

        Matcher externalMatcher = fullQuotePattern.matcher(href);
        if (externalMatcher.matches()) {
            String board = externalMatcher.group(1);
            int threadId = Integer.parseInt(externalMatcher.group(2));
            String postNo = externalMatcher.group(3);
            int postId = postNo == null ? -1 : Integer.parseInt(postNo);

            if (board.equals(post.board.code) && callback.isInternal(postId)) {
                //link to post in same thread with post number (>>post); usually this is a almost fully qualified link
                t = Type.QUOTE;
                value = postId;
            } else {
                //link to post not in same thread with post number (>>post or >>>/board/post)
                //in the case of an archive, set the type to be an archive link
                t = post.board.site instanceof ExternalSiteArchive ? Type.ARCHIVE : Type.THREAD;
                value = new ThreadLink(board, threadId, postId);
                if (href.contains("post") && post.board.site instanceof ExternalSiteArchive) {
                    // this is an archive post link that needs to be resolved into a threadlink
                    value = new ResolveLink(post.board.site, board, threadId);
                }
            }
        } else {
            Matcher quoteMatcher = quotePattern.matcher(href);
            if (quoteMatcher.matches()) {
                //link to post in the same thread with post number (>>post); usually this is a #num href
                t = Type.QUOTE;
                value = Integer.parseInt(quoteMatcher.group(1));
            } else {
                Matcher boardLinkMatcher = boardLinkPattern.matcher(href);
                Matcher boardLinkMatcher8Chan = boardLinkPattern8Chan.matcher(href);
                Matcher boardSearchMatcher = boardSearchPattern.matcher(href);
                if (boardLinkMatcher.matches() || boardLinkMatcher8Chan.matches()) {
                    //board link
                    t = Type.BOARD;
                    value = (boardLinkMatcher.matches() ? boardLinkMatcher : boardLinkMatcher8Chan).group(1);
                } else if (boardSearchMatcher.matches()) {
                    //search link
                    String board = boardSearchMatcher.group(1);
                    String search;
                    try {
                        search = URLDecoder.decode(boardSearchMatcher.group(2), "US-ASCII");
                    } catch (UnsupportedEncodingException e) {
                        search = boardSearchMatcher.group(2);
                    }
                    t = Type.SEARCH;
                    value = new SearchLink(board, search);
                } else {
                    if (href.startsWith("javascript:")) {
                        //this link would run javascript on the source webpage, open this in a webview
                        t = Type.JAVASCRIPT;
                    } else {
                        //normal link
                        t = Type.LINK;
                    }
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

    public static class Link {
        public Type type;
        public CharSequence key;
        public Object value;
    }

    /**
     * A board, thread, and postId combination to identify a thread.
     * Used for ExternalSiteArchives.
     */
    public static class ThreadLink {
        public String boardCode;
        public int threadId;
        public int postId;

        public ThreadLink(String boardCode, int threadId, int postId) {
            this.boardCode = boardCode;
            this.threadId = threadId;
            this.postId = postId;
        }
    }

    /**
     * Resolve a board and postId to a ThreadLink.
     * Used for ExternalSiteArchives.
     */
    public static class ResolveLink {
        public Board board;
        public int postId;

        public ResolveLink(Site site, String boardCode, int postId) {
            this.board = Board.fromSiteNameCode(site, "", boardCode);
            this.postId = postId;
        }

        public void resolve(@NonNull ResolveCallback callback, @NonNull ResolveParser parser) {
            NetUtils.makeJsonRequest(((ArchiveEndpoints) board.site.endpoints()).resolvePost(board.code, postId),
                    new NetUtilsClasses.MainThreadResponseResult<>(new ResponseResult<ThreadLink>() {
                        @Override
                        public void onFailure(Exception e) {
                            callback.onProcessed(null);
                        }

                        @Override
                        public void onSuccess(ThreadLink result) {
                            callback.onProcessed(result);
                        }
                    }),
                    parser,
                    NetUtilsClasses.NO_CACHE,
                    5000
            );
        }

        public interface ResolveCallback {
            void onProcessed(@Nullable ThreadLink result);
        }

        public static class ResolveParser
                implements NetUtilsClasses.Converter<ThreadLink, JsonReader> {
            private final ResolveLink sourceLink;

            public ResolveParser(ResolveLink source) {
                sourceLink = source;
            }

            @Override
            public ThreadLink convert(JsonReader reader) {
                return ((ArchiveSiteUrlHandler) sourceLink.board.site.resolvable()).resolveToThreadLink(sourceLink,
                        reader
                );
            }
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
