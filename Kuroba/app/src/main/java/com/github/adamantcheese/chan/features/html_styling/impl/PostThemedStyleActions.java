package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.ARCHIVE;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.BOARD;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.JAVASCRIPT;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.LINK;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.QUOTE;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.SEARCH;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.SPOILER;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.THREAD;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.QUOTE_COLOR;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.Link;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.SearchLink;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.features.html_styling.base.ChainStyleAction;
import com.github.adamantcheese.chan.features.html_styling.base.PostThemedStyleAction;
import com.github.adamantcheese.chan.ui.text.CustomTypefaceSpan;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.common.io.Files;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

public class PostThemedStyleActions {
    public static final String EXIF_INFO_STRING = "[EXIF data available. Click here to view.]";

    public static PostThemedStyleAction CHAN_ANCHOR = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.Callback callback
        ) {
            Link handlerLink = null;
            try {
                handlerLink = matchAnchor(node, text, post, callback);
            } catch (Exception e) {
                Logger.w(this, "Failed to parse an element, leaving as plain text.");
            }

            if (handlerLink != null) {
                return addReply(theme, callback, post, handlerLink);
            } else {
                return text == null ? "" : text;
            }
        }
    };

    @NonNull
    private static Link matchAnchor(
            @NonNull Node anchor,
            @Nullable CharSequence text,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    ) {
        String href = anchor.attr("href");
        HttpUrl hrefUrl = HttpUrl.get(anchor.absUrl("href"));
        List<String> hrefSegments = hrefUrl.pathSegments();

        int threadNo = -1; // no known thread number
        try {
            // on the last segment of the url, strip anything that isn't a number and parse it
            threadNo = Integer.parseInt(hrefSegments.get(hrefSegments.size() - 1).replaceAll("[^\\d]", ""));
        } catch (Exception ignored) {}
        // if there are multiple segments, get the third from the last otherwise the second from the last
        int segments = hrefSegments.size();
        String board = null;
        try {
            board = segments - 3 >= 0 ? hrefSegments.get(segments - 3) : hrefSegments.get(segments - 2);
        } catch (Exception ignored) {}
        int postNo = -1;

        String fragment = hrefUrl.fragment();
        fragment = fragment == null ? "~" : fragment;
        // quote fragment
        try {
            postNo = Integer.parseInt(fragment.substring(fragment.charAt(0) == 'p' ? 1 : 0));
        } catch (Exception ignored) {}

        PostLinkable.Type t;
        Object value;

        if (board != null && fragment.charAt(0) == 's') {
            t = SEARCH;
            value = new SearchLink(board, fragment.substring(2));
        } else if (board != null && threadNo == -1 && postNo == -1) {
            t = BOARD;
            value = board;
        } else if (post.board.code.equals(board)) {
            if (callback.isInternal(postNo)) {
                //link to post in same thread with post number (>>post); usually this is a almost fully qualified link
                t = QUOTE;
                value = postNo;
            } else {
                //link to post not in same thread with post number (>>post or >>>/board/post)
                //in the case of an archive, set the type to be an archive link
                t = post.board.site instanceof ExternalSiteArchive ? ARCHIVE : THREAD;
                value = new ThreadLink(board, threadNo, postNo);
                if (href.contains("post") && post.board.site instanceof ExternalSiteArchive) {
                    // this is an archive post link that needs to be resolved into a threadlink
                    value = new ResolveLink((ExternalSiteArchive) post.board.site, board, threadNo);
                }
            }
        } else if (href.startsWith("javascript:")) {
            t = JAVASCRIPT;
            value = href;
        } else {
            t = LINK;
            value = href;
        }

        return new Link(t, text, value);
    }

    @NonNull
    private static CharSequence addReply(
            @NonNull Theme theme,
            @NonNull PostParser.Callback callback,
            @NonNull Post.Builder post,
            @NonNull Link handlerLink
    ) {
        if (handlerLink.type == THREAD) {
            handlerLink.key = TextUtils.concat(handlerLink.key, " →");
        }

        if (handlerLink.type == ARCHIVE && (
                (handlerLink.value instanceof ThreadLink && ((ThreadLink) handlerLink.value).postId == -1)
                        || handlerLink.value instanceof ResolveLink)) {
            handlerLink.key = TextUtils.concat(handlerLink.key, " →");
        }

        if (handlerLink.type == QUOTE) {
            int postNo = (int) handlerLink.value;
            post.repliesTo(Collections.singleton(postNo));

            // Append (OP) when it's a reply to OP
            if (postNo == post.opId) {
                handlerLink.key = TextUtils.concat(handlerLink.key, " (OP)");
            }

            // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
            if (callback.isSaved(postNo)) {
                if (post.isSavedReply) {
                    handlerLink.key = TextUtils.concat(handlerLink.key, " (Me)");
                } else {
                    handlerLink.key = TextUtils.concat(handlerLink.key, " (You)");
                }
            }
        }

        return span(handlerLink.key, new PostLinkable(theme, handlerLink.value, handlerLink.type));
    }

    // replaces img tags with an attached image, and any alt-text will become a spoilered text item
    public static PostThemedStyleAction IMAGE = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.Callback callback
        ) {
            try {
                SpannableStringBuilder ret = new SpannableStringBuilder(text == null ? "" : text);
                if (node.hasAttr("alt")) {
                    String alt = node.attr("alt");
                    if (!alt.isEmpty()) {
                        ret.append(span(alt, new PostLinkable(theme, alt, SPOILER))).append(" ");
                    }
                }
                HttpUrl src = HttpUrl.get(node.attr("src"));
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
                return text == null ? "" : text;
            }
        }
    };

    // This is used on /p/ for exif data.
    public static PostThemedStyleAction TABLE = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.Callback callback
        ) {
            SpannableStringBuilder parts = new SpannableStringBuilder();
            Elements tableRows = ((Element) node).getElementsByTag("tr");
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
            return span(EXIF_INFO_STRING, new PostLinkable(theme, new Object(), PostLinkable.Type.OTHER) {
                @Override
                public void onClick(@NonNull View widget) {
                    AlertDialog dialog = getDefaultAlertBuilder(widget.getContext()).setMessage(parts)
                            .setPositiveButton(R.string.ok, null)
                            .create();
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.show();
                }
            }, new ForegroundColorSpanHashed(AndroidUtils.getThemeAttrColor(theme, R.attr.post_inline_quote_color)));
        }
    };

    public static PostThemedStyleAction SJIS = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.Callback callback
        ) {
            return span(
                    "[SJIS art available. Click here to view.]",
                    new CustomTypefaceSpan("",
                            Typeface.createFromAsset(getAppContext().getAssets(), "font/submona.ttf")
                    ),
                    new PostLinkable(theme, new Object(), PostLinkable.Type.OTHER) {
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
                    new ForegroundColorSpanHashed(AndroidUtils.getThemeAttrColor(theme, R.attr.post_inline_quote_color))
            );
        }
    };

    public static PostThemedStyleAction DEADLINK = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.Callback callback
        ) {
            try {
                //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
                int postNo = Integer.parseInt(((Element) node).text().substring(2));
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
                            ARCHIVE
                    );
                    return span(text, newLinkable);
                }
            } catch (Exception ignored) {
            }
            return new ChainStyleAction(STRIKETHROUGH).chain(QUOTE_COLOR.with(theme)).style(node, text);
        }
    };
}
