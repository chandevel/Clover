package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.QUOTE_COLOR;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.INTERNAL_SPOILER_THUMB_URL;
import static com.github.adamantcheese.chan.utils.StringUtils.RenderOrder.RENDER_NORMAL;
import static com.github.adamantcheese.chan.utils.StringUtils.makeSpanOptions;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.graphics.Typeface;
import android.text.*;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.*;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.*;
import com.github.adamantcheese.chan.features.html_styling.base.*;
import com.github.adamantcheese.chan.ui.text.CustomTypefaceSpan;
import com.github.adamantcheese.chan.ui.text.post_linkables.*;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.common.io.Files;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.List;
import java.util.regex.*;

import javax.inject.Inject;

import okhttp3.Headers;
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
                @NonNull PostParser.PostParserCallback callback
        ) {
            PostLinkable<?> linkable = generateLinkableForAnchor(node, theme, post, callback);
            // it is possible that the LINK action has already generated things that we will generate again
            // that is, a node like <a href="https://www.example.com">https://www.example.com</a> is being processed here
            // but the inner text has already been autolinked and we don't want to make another link on top of it
            // we check existing spans against the one we generated to see if we'd style the same thing; if so, abort
            if (text instanceof Spanned) {
                Spanned txt = (Spanned) text;
                PostLinkable<?>[] linkables = txt.getSpans(0, text.length(), linkable.getClass());
                for (PostLinkable<?> pl : linkables) {
                    if (pl.value.equals(linkable.value)) return text;
                }
            }
            return styleWithLinkable(callback, post, linkable, text);
        }
    };

    @NonNull
    private static PostLinkable<?> generateLinkableForAnchor(
            @NonNull Node anchor,
            Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.PostParserCallback callback
    ) {
        String href = anchor.attr("href");
        HttpUrl hrefUrl = null;
        try {
            hrefUrl = HttpUrl.get(anchor.absUrl("href"));
        } catch (Exception ignored) {}
        if (hrefUrl == null) {
            return new ParserLinkLinkable(theme, href);
        }
        List<String> hrefSegments = hrefUrl.pathSegments();
        for (int i = hrefSegments.size() - 1; i >= 0; i--) {
            String segment = hrefSegments.get(i);
            if (segment.isEmpty()) hrefSegments.remove(segment);
        }

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
            //@formatter:off
            postNo = Integer.parseInt(
                    fragment.substring(fragment.charAt(0) == 'p' || fragment.charAt(0) == 'q' ? 1 : 0));
            //@formatter:on
        } catch (Exception ignored) {}

        if (board != null && fragment.charAt(0) == 's') {
            return new SearchLinkable(theme, new SearchLink(board, fragment.substring(2)));
        } else if (board != null && threadNo == -1 && postNo == -1) {
            return new BoardLinkable(theme, board);
        } else if (board != null && !board.equals(post.board.code) && threadNo != -1 && postNo != -1) {
            return new ThreadLinkable(theme, new ThreadLink(board, threadNo, postNo));
        } else if (post.board.code.equals(board)) {
            if (callback.isInternal(postNo)) {
                post.repliesTo(Collections.singleton(postNo));
                //link to post in same thread with post number (>>post); usually this is a almost fully qualified link
                return new QuoteLinkable(theme, postNo);
            } else {
                //link to post not in same thread with post number (>>post or >>>/board/post)
                //in the case of an archive, set the type to be an archive link
                ThreadLink threadLink = new ThreadLink(board, threadNo, postNo);
                return post.board.site instanceof ExternalSiteArchive ? new ArchiveLinkable(
                        theme,
                        href.contains("post")
                                ? new ResolveLink((ExternalSiteArchive) post.board.site, board, threadNo)
                                : threadLink
                ) : new ThreadLinkable(theme, threadLink);
            }
        } else {
            return new ParserLinkLinkable(theme, hrefUrl.toString());
        }
    }

    @NonNull
    private static CharSequence styleWithLinkable(
            @NonNull PostParser.PostParserCallback callback,
            @NonNull Post.Builder post,
            @NonNull PostLinkable<?> linkable,
            CharSequence text
    ) {
        if (linkable instanceof StyleActionTextAdjuster) {
            text = ((StyleActionTextAdjuster) linkable).adjust(text);
        }

        if (linkable instanceof QuoteLinkable) {
            int postNo = (int) linkable.value;

            // Append (OP) when it's a reply to OP
            if (postNo == post.opId) {
                text = TextUtils.concat(text, " (OP)");
            }

            // Append (You) when it's a reply to a saved reply, (Me) if it's a self reply
            if (callback.isSaved(postNo)) {
                if (post.isSavedReply) {
                    text = TextUtils.concat(text, " (Me)");
                } else {
                    text = TextUtils.concat(text, " (You)");
                }
            }
        }

        return span(text, linkable);
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
                @NonNull PostParser.PostParserCallback callback
        ) {
            try {
                SpannableStringBuilder ret = new SpannableStringBuilder(text == null ? "" : text);
                if (node.hasAttr("alt")) {
                    String alt = node.attr("alt");
                    if (!alt.isEmpty()) {
                        ret.append(span(alt, new SpoilerLinkable(theme, alt))).append(" ");
                    }
                }
                HttpUrl src = HttpUrl.get(node.attr("src"));
                PostImage i = new PostImage.Builder()
                        .imageUrl(src)
                        .thumbnailUrl(src)
                        .spoilerThumbnailUrl(src)
                        .filename(Files.getNameWithoutExtension(src.toString()))
                        .extension(Files.getFileExtension(src.toString()))
                        .build();
                post.images(Collections.singletonList(i));
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
                @NonNull PostParser.PostParserCallback callback
        ) {
            SpannableStringBuilder parts = new SpannableStringBuilder();
            Elements tableRows = ((Element) node).getElementsByTag("tr");
            for (int i = 0; i < tableRows.size(); i++) {
                Element tableRow = tableRows.get(i);
                if (tableRow.text().isEmpty()) continue;
                Elements tableDatas = tableRow.getElementsByTag("td");
                for (int j = 0; j < tableDatas.size(); j++) {
                    Element tableData = tableDatas.get(j);

                    if (tableData.getElementsByTag("b").size() > 0) {
                        parts.append(span(tableData.text(), new StyleSpan(Typeface.BOLD), new UnderlineSpan()));
                    } else {
                        parts.append(tableData.text());
                    }

                    if (j < tableDatas.size() - 1) parts.append(": ");
                }

                if (i < tableRows.size() - 1) parts.append("\n");
            }

            // Overrides the text (possibly) parsed by child nodes.
            return span(EXIF_INFO_STRING, new PopupItemLinkable(theme) {
                @Override
                public void onClick(@NonNull View widget) {
                    AlertDialog dialog = getDefaultAlertBuilder(widget.getContext())
                            .setMessage(parts)
                            .setPositiveButton(R.string.ok, null)
                            .create();
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.show();
                }
            });
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
                @NonNull PostParser.PostParserCallback callback
        ) {
            if (text == null) return "";
            return span("[SJIS art available. Click here to view.]", new PopupItemLinkable(theme) {
                @Override
                public void onClick(@NonNull View widget) {
                    TextView sjisView = new TextView(widget.getContext());
                    sjisView.setMovementMethod(new ScrollingMovementMethod());
                    sjisView.setHorizontallyScrolling(true);
                    updatePaddings(sjisView, dp(16));
                    //@formatter:off
                    sjisView.setText(span(text.toString(),
                            new CustomTypefaceSpan("",
                                    Typeface.createFromAsset(widget.getContext().getAssets(), "font/submona.ttf")
                            )
                    ));
                    //@formatter:on
                    AlertDialog dialog = getDefaultAlertBuilder(widget.getContext())
                            .setView(sjisView)
                            .setPositiveButton(R.string.close, null)
                            .create();
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.show();
                }
            });
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
                @NonNull PostParser.PostParserCallback callback
        ) {
            try {
                //crossboard thread links in the OP are likely not thread links, so just let them error out on the parseInt
                int postNo = Integer.parseInt(((Element) node).text().substring(2));
                List<ExternalSiteArchive> boards = ArchivesManager.getInstance().archivesForBoard(post.board);
                if (!boards.isEmpty()) {
                    Site forThisSite = post.board.site;
                    String forThisBoard = post.board.code;
                    ArchiveLinkable newLinkable = new ArchiveLinkable(
                            theme,
                            // if the deadlink is in an external archive, set a resolve link
                            // if the deadlink is in any other site, we don't have enough info to properly link to stuff, so
                            // we assume that deadlinks in an OP are previous threads
                            // and any deadlinks in other posts are deleted posts in the same thread
                            forThisSite instanceof ExternalSiteArchive
                                    ? new ResolveLink((ExternalSiteArchive) forThisSite, forThisBoard, postNo)
                                    : new ThreadLink(forThisBoard, post.op ? postNo : post.opId, post.op ? -1 : postNo)
                    );
                    return span(text, newLinkable);
                }
            } catch (Exception ignored) {
            }
            return new ChainStyleAction(STRIKETHROUGH).chain(QUOTE_COLOR.with(theme)).style(node, text);
        }
    };

    // matches stuff like file.jpg or file?format=jpg&name=orig
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)(?:\\.|\\?.+=)(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac|wav)(?:.*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] NO_THUMB_LINK_SUFFIXES =
            {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac", "wav"};

    public static PostThemedStyleAction EMBED_IMAGES = new PostThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.PostParserCallback callback
        ) {
            if (!ChanSettings.parsePostImageLinks.get()) return text == null ? "" : text;
            if (!(text instanceof Spanned)) return text == null ? "" : text;
            Spanned toSearch = (Spanned) text;
            ParserLinkLinkable[] linkables = toSearch.getSpans(0, toSearch.length(), ParserLinkLinkable.class);
            for (ParserLinkLinkable linkable : linkables) {
                Matcher matcher = IMAGE_URL_PATTERN.matcher(linkable.value);
                if (matcher.matches()) {
                    boolean noThumbnail = StringUtils.endsWithAny(linkable.value, NO_THUMB_LINK_SUFFIXES);

                    HttpUrl imageUrl = HttpUrl.get(linkable.value);
                    // ignore saucenao links, not actual images
                    if (imageUrl.host().equals("saucenao.com")) {
                        continue;
                    }

                    PostImage inlinedImage = new PostImage.Builder().serverFilename(matcher.group(1))
                            //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                            .thumbnailUrl(noThumbnail ? INTERNAL_SPOILER_THUMB_URL : HttpUrl.get(linkable.value))
                            .spoilerThumbnailUrl(INTERNAL_SPOILER_THUMB_URL)
                            .imageUrl(imageUrl)
                            .filename(matcher.group(1))
                            .extension(matcher.group(2))
                            .spoiler(true)
                            .isInlined()
                            .size(-1)
                            .build();

                    post.images(Collections.singletonList(inlinedImage));

                    NetUtils.makeHeadersRequest(imageUrl, new NetUtilsClasses.ResponseResult<Headers>() {
                        @Override
                        public void onFailure(Exception e) {}

                        @Override
                        public void onSuccess(Headers result) {
                            String size = result.get("Content-Length");
                            inlinedImage.size = size == null ? 0 : Long.parseLong(size);
                        }
                    });
                }
            }
            return text;
        }
    };

    public static PostThemedStyleAction FILTER_DEBUG = new PostThemedStyleAction() {
        @Inject
        private FilterEngine filterEngine;

        {
            inject(this);
        }

        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node,
                @Nullable CharSequence text,
                @NonNull Theme theme,
                @NonNull Post.Builder post,
                @NonNull PostParser.PostParserCallback callback
        ) {
            if (!ChanSettings.debugFilters.get()) return text == null ? "" : text;
            SpannableString builder = new SpannableString(text);
            for (Filter f : filterEngine.getEnabledFilters()) {
                if (filterEngine.matchesBoard(f, post.board)) {
                    MatchResult result = filterEngine.getMatch(f, FilterType.COMMENT, text, false);
                    if (result != null) {
                        builder.setSpan(
                                new FilterDebugLinkable(ThemeHelper.getTheme(), f.pattern),
                                result.start(),
                                result.end(),
                                makeSpanOptions(RENDER_NORMAL)
                        );
                    }
                }
            }
            return builder;
        }
    };
}
