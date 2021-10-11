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
package com.github.adamantcheese.chan.core.site.parser.style.comment;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.PostLinkable.Type;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.parser.PostParser.Callback;
import com.github.adamantcheese.chan.core.site.parser.style.HtmlAction;
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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.CHOMP;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.CODE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.INLINE_QUOTE_COLOR;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.MONOSPACE;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.NULLIFY;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.SPOILER;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.SRC_ATTR;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

/**
 * This has specific rules for chan boards (see the "handle" methods) that make display a bit nicer or functional,
 * as well a bunch of default styling actions that are applied.
 */
public class ChanCommentAction
        extends HtmlAction {
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

    private static Typeface submona;

    public ChanCommentAction() {
        super();

        // text modifying
        mapTagToRule("span", "abbr", NULLIFY);
        mapTagToRule("iframe", SRC_ATTR);

        // complex text
        mapTagToRule("span", "quote", INLINE_QUOTE_COLOR);
        mapTagToRule("pre", MONOSPACE);
        mapTagToRule("pre", "prettyprint", CODE, CHOMP);
        mapTagToRule("span", "spoiler", SPOILER);
        mapTagToRule("s", SPOILER);

        // functional text
        mapTagToRule("a", this::handleAnchor);
        mapTagToRule("span", "deadlink", this::handleDead);
        mapTagToRule("span", "sjis", this::handleSJIS);
        mapTagToRule("table", this::handleTable);
        mapTagToRule("img", this::handleImage);
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

    @NonNull
    private SpannedString handleAnchor(
            @NonNull Element anchor,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        Link handlerLink = null;
        try {
            handlerLink = matchAnchor(anchor, text, post, callback);
        } catch (Exception e) {
            Logger.w(this, "Failed to parse an element, leaving as plain text.");
        }

        if (handlerLink != null) {
            return addReply(theme, callback, post, handlerLink);
        } else {
            return new SpannedString(text);
        }
    }

    // replaces img tags with an attached image, and any alt-text will become a spoilered text item
    @NonNull
    private SpannedString handleImage(
            @NonNull Element image,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        try {
            SpannableStringBuilder ret = new SpannableStringBuilder(text);
            if (image.hasAttr("alt")) {
                String alt = image.attr("alt");
                if (!alt.isEmpty()) {
                    Spannable tmp = new SpannableString(alt + " ");
                    tmp.setSpan(new PostLinkable(theme, alt, Type.SPOILER), 0, alt.length(), SPAN_INCLUSIVE_EXCLUSIVE);
                    ret.append(tmp);
                }
            }
            HttpUrl src = HttpUrl.get(image.attr("src"));
            PostImage i = new PostImage.Builder().imageUrl(src)
                    .thumbnailUrl(src)
                    .spoilerThumbnailUrl(src)
                    .filename(Files.getNameWithoutExtension(src.toString()))
                    .extension(Files.getFileExtension(src.toString()))
                    .build();
            if (post.images.size() < 5 && !post.images.contains(i)) {
                post.images(Collections.singletonList(i));
            }
            return new SpannedString(ret);
        } catch (Exception e) {
            return new SpannedString(text);
        }
    }

    private SpannedString addReply(
            @NonNull Theme theme, @NonNull Callback callback, @NonNull Post.Builder post, @NonNull Link handlerLink
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

        return span(handlerLink.key, new PostLinkable(theme, handlerLink.value, handlerLink.type));
    }

    // This is used on /p/ for exif data.
    @NonNull
    public SpannedString handleTable(
            @NonNull Element table,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        SpannableStringBuilder parts = new SpannableStringBuilder();
        Elements tableRows = table.getElementsByTag("tr");
        for (int i = 0; i < tableRows.size(); i++) {
            Element tableRow = tableRows.get(i);
            if (!tableRow.text().isEmpty()) {
                Elements tableDatas = tableRow.getElementsByTag("td");
                for (int j = 0; j < tableDatas.size(); j++) {
                    Element tableData = tableDatas.get(j);

                    if (tableData.getElementsByTag("b").size()
                            > 0) { // if it has a bold element, the entire thing is bold; should only bold the necessary part
                        parts.append(span(tableData.text(), new StyleSpan(Typeface.BOLD), new UnderlineSpan()));
                    }

                    if (j < tableDatas.size() - 1) parts.append(": ");
                }

                if (i < tableRows.size() - 1) parts.append("\n");
            }
        }

        // Overrides the text (possibly) parsed by child nodes.
        return span(
                EXIF_INFO_STRING,
                new PostLinkable(theme, new Object(), Type.OTHER) {
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
                new AbsoluteSizeSpanHashed((int) sp(12f))
        );
    }

    @NonNull
    public SpannedString handleSJIS(
            @NonNull Element sjis,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        if (submona == null) {
            submona = Typeface.createFromAsset(getAppContext().getAssets(), "font/submona.ttf");
        }
        return span(
                "[SJIS art available. Click here to view.]",
                new CustomTypefaceSpan("", submona),
                new PostLinkable(theme, new Object(), Type.OTHER) {
                    @Override
                    public void onClick(@NonNull View widget) {
                        TextView sjisView = new TextView(widget.getContext());
                        sjisView.setMovementMethod(new ScrollingMovementMethod());
                        sjisView.setHorizontallyScrolling(true);
                        updatePaddings(sjisView, dp(16), dp(16), dp(16), dp(16));
                        sjisView.setText(text);
                        AlertDialog dialog = getDefaultAlertBuilder(widget.getContext()).setView(sjisView)
                                .setPositiveButton(R.string.close, null)
                                .create();
                        dialog.setCanceledOnTouchOutside(true);
                        dialog.show();
                    }
                },
                new ForegroundColorSpanHashed(getAttrColor(theme.resValue, R.attr.post_inline_quote_color)),
                new AbsoluteSizeSpanHashed((int) sp(12f))
        );
    }

    @NonNull
    public SpannedString handleDead(
            @NonNull Element deadlink,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
        try {
            if (!(post.board.site instanceof Chan4)) return new SpannedString(text); //4chan only
            int postNo = Integer.parseInt(deadlink.text().substring(2));
            List<ExternalSiteArchive> boards = ArchivesManager.getInstance().archivesForBoard(post.board);
            if (!boards.isEmpty()) {
                Site forThisSite = post.board.site;
                String forThisBoard = post.board.code;
                PostLinkable newLinkable = new PostLinkable(
                        theme,
                        // if the deadlink is in an external archive, set a resolve link
                        // if the deadlink is in any other site, we don't have enough info to properly link to stuff, so
                        // we assume that deadlinks in an OP are previous threads
                        // and any deadlinks in other posts are deleted posts in the same thread
                        forThisSite instanceof ExternalSiteArchive
                                ? new ResolveLink((ExternalSiteArchive) forThisSite, forThisBoard, postNo)
                                : new ThreadLink(forThisBoard, post.op ? postNo : post.opId, post.op ? -1 : postNo),
                        Type.ARCHIVE
                );
                return span(text, newLinkable, new StrikethroughSpan());
            }
        } catch (Exception ignored) {
        }
        return new SpannedString(text);
    }

    @NonNull
    public Link matchAnchor(
            @NonNull Element anchor, @NonNull Spanned text, @NonNull Post.Builder post, @NonNull Callback callback
    ) {
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

            if (post.board.code.equals(board) && callback.isInternal(postId)) {
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
                    value = new ResolveLink((ExternalSiteArchive) post.board.site, board, threadId);
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

        return new Link(t, text, value);
    }
}
