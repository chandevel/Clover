package com.github.adamantcheese.chan.core.site.archives;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.NO_OP;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.INLINE_QUOTE_COLOR;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.ARCHIVE_MISSING_THUMB_URL;
import static com.github.adamantcheese.chan.utils.BuildConfigUtils.DEFAULT_SPOILER_IMAGE_URL;
import static com.github.adamantcheese.chan.utils.HttpUrlUtilsKt.trimmedPathSegments;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlTagAction;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.google.common.io.Files;

import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.TextNode;

import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

/**
 * A site that uses FoolFuuka as the backend.
 */
public class FoolFuukaArchive
        extends ExternalSiteArchive {

    private FoolFuukaReader reader;

    public FoolFuukaArchive(
            String domain, String name, List<String> boardCodes, boolean searchEnabled
    ) {
        super(domain, name, boardCodes, searchEnabled);
    }

    private class FoolFuukaReader
            extends ExternalArchiveSiteContentReader {

        private final PostParser parser = new PostParser(new FoolFuukaCommentAction()) {
            @Override
            public String createQuoteElementString(Post.Builder post) {
                return "<span class=\"greentext\"><a href=\"https://"
                        + domain
                        + "/"
                        + post.board.code
                        + "/thread/"
                        + post.opId
                        + "/#$1\">&gt;&gt;$1</a></span>";
            }
        };

        @Override
        public PostParser getParser() {
            return parser;
        }

        @Override
        public void loadThread(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            reader.beginObject(); // start JSON
            reader.nextName(); // the op number, duplicated in the post object itself
            reader.beginObject();
            // OP object
            readPostObject(reader, queue);

            // in the event of only an OP, the regular posts section is not there
            if (reader.peek() != JsonToken.END_OBJECT) {
                reader.nextName(); // posts object
                reader.beginObject();
                // Posts object
                while (reader.hasNext()) {
                    readPostObject(reader, queue);
                }
                reader.endObject();
            }

            reader.endObject();
            reader.endObject();
        }

        @Override
        public void readPostObject(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            Post.Builder builder = new Post.Builder();
            builder.board(queue.loadable.board);
            reader.nextName(); // "op" or post number; not necessary as it's in the rest of the data so ignore this
            reader.beginObject(); // post object itself
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "num":
                        builder.no(reader.nextInt());
                        break;
                    case "thread_num":
                        builder.opId(reader.nextInt());
                        break;
                    case "op":
                        builder.op(reader.nextInt() == 1);
                        queue.setOp(builder.clone());
                        break;
                    case "timestamp":
                        builder.setUnixTimestampSeconds(reader.nextLong());
                        break;
                    case "capcode":
                        String capcode = reader.nextString();
                        if ("N".equals(capcode)) break;
                        builder.moderatorCapcode(capcode);
                        break;
                    case "name":
                        builder.name(reader.nextString());
                        break;
                    case "trip":
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                        } else {
                            builder.tripcode(reader.nextString());
                        }
                        break;
                    case "title":
                        if (builder.op && reader.peek() != JsonToken.NULL) {
                            String title = reader.nextString();
                            builder.subject(title);
                            queue.loadable.title = title;
                        } else {
                            reader.skipValue();
                        }
                        break;
                    case "comment_processed":
                        String comment = reader.nextString();
                        comment = comment.replaceAll("\\n", ""); // comment contains extra newlines, remove em
                        builder.comment(comment);

                        if (builder.op && TextUtils.isEmpty(builder.getSubject())) {
                            if (!TextUtils.isEmpty(comment)) {
                                queue.loadable.title = comment.subSequence(0, Math.min(comment.length(), 200)) + "";
                            } else {
                                queue.loadable.title = "/" + builder.board.code + "/" + builder.opId;
                            }
                        }
                        break;
                    case "sticky":
                        builder.sticky(reader.nextInt() == 1);
                        break;
                    case "locked":
                        builder.closed(reader.nextInt() == 1);
                        break;
                    case "media":
                        if (reader.peek() == JsonToken.NULL) {
                            reader.skipValue();
                            continue;
                        }
                        PostImage.Builder imageBuilder = new PostImage.Builder();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String mediaKey = reader.nextName();
                            switch (mediaKey) {
                                case "spoiler":
                                    imageBuilder.spoiler(reader.nextInt() == 1);
                                    imageBuilder.spoilerThumbnailUrl(DEFAULT_SPOILER_IMAGE_URL);
                                    break;
                                case "media_w":
                                    imageBuilder.imageWidth(reader.nextInt());
                                    break;
                                case "media_h":
                                    imageBuilder.imageHeight(reader.nextInt());
                                    break;
                                case "media_size":
                                    imageBuilder.size(reader.nextLong());
                                    break;
                                case "media_filename":
                                    String filename = reader.nextString();
                                    imageBuilder.filename(Files.getNameWithoutExtension(filename));
                                    imageBuilder.extension(Files.getFileExtension(filename));
                                    break;
                                case "media_hash":
                                    imageBuilder.fileHash(reader.nextString(), true);
                                    break;
                                case "remote_media_link":
                                case "media_link":
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                        if (imageBuilder.hasImageUrl()) break;
                                        imageBuilder.imageUrl(ARCHIVE_MISSING_THUMB_URL);
                                    } else {
                                        String imageUrl = reader.nextString();
                                        imageUrl = StringUtil.resolve("https://" + domain, imageUrl);
                                        try {
                                            imageBuilder.imageUrl(HttpUrl.get(imageUrl));
                                        } catch (Exception e) {
                                            imageBuilder.imageUrl(ARCHIVE_MISSING_THUMB_URL);
                                        }
                                    }
                                    break;
                                case "media_orig":
                                    imageBuilder.serverFilename(Files.getNameWithoutExtension(reader.nextString()));
                                    break;
                                case "thumb_link":
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                        imageBuilder.thumbnailUrl(ARCHIVE_MISSING_THUMB_URL);
                                    } else {
                                        String thumbUrl = reader.nextString();
                                        thumbUrl = StringUtil.resolve("https://" + domain, thumbUrl);
                                        try {
                                            imageBuilder.thumbnailUrl(HttpUrl.get(thumbUrl));
                                        } catch (Exception e) {
                                            imageBuilder.thumbnailUrl(ARCHIVE_MISSING_THUMB_URL);
                                        }
                                    }
                                    break;
                                default:
                                    reader.skipValue();
                            }
                        }
                        reader.endObject();
                        builder.images(Collections.singletonList(imageBuilder.build()));
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            queue.addForParse(builder);
        }
    }

    private static class FoolFuukaCommentAction
            extends ChanCommentAction {
        @Override
        public HtmlTagAction addSpecificActions(
                Theme theme, Post.Builder post, PostParser.PostParserCallback callback
        ) {
            HtmlTagAction base = super.addSpecificActions(theme, post, callback);
            HtmlTagAction newAction = new HtmlTagAction();
            // for some reason, stuff is wrapped in a "greentext" span if it starts with a > regardless of it is greentext or not
            // the default post parser has already handled any inner tags by the time that it has gotten to this case, since
            // deepest nodes are processed first
            // in this case, we just want to return the text that has already been processed inside of this "greentext" node
            // otherwise duplicate PostLinkables will be generated
            newAction.mapTagToRule(
                    "span",
                    "greentext",
                    (node, text) -> node.childNodeSize() == 1 && node.childNode(0) instanceof TextNode
                            ? INLINE_QUOTE_COLOR.with(theme).style(node, text)
                            : NO_OP.style(node, text)
            );
            return base.mergeWith(newAction);
        }
    }

    @Override
    public ArchiveSiteUrlHandler resolvable() {
        return new ArchiveSiteUrlHandler() {
            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                HttpUrl.Builder url =
                        new HttpUrl.Builder().scheme("https").host(domain).addPathSegment(loadable.boardCode);
                if (loadable.isThreadMode()) {
                    return url
                            .addPathSegment("thread")
                            .addPathSegment(String.valueOf(loadable.no))
                            .fragment(postNo > 0 ? String.valueOf(postNo) : null)
                            .toString();
                } else {
                    return url.toString();
                }
            }

            @Override
            public Loadable resolveLoadable(Site site, HttpUrl url) {
                List<String> parts = trimmedPathSegments(url);

                if (!parts.isEmpty()) {
                    String boardCode = parts.get(0);
                    Board board = site.board(boardCode);
                    if (board != null) {
                        if (parts.size() == 1) {
                            // Board mode
                            return Loadable.forCatalog(board);
                        } else {
                            // Thread mode
                            int no = -1;
                            try {
                                no = Integer.parseInt(parts.get(2));
                            } catch (Exception ignored) {
                            }

                            int post = -1;
                            try {
                                post = Integer.parseInt(url.fragment());
                            } catch (Exception ignored) {
                            }

                            if (no >= 0) {
                                Loadable loadable = Loadable.forThread(board, no, "", false);
                                if (post >= 0) {
                                    loadable.markedNo = post;
                                }

                                return loadable;
                            }
                        }
                    }
                }

                return null;
            }

            @Override
            public ThreadLink resolveToThreadLink(ResolveLink source, JsonReader reader) {
                try {
                    reader.beginObject(); //begin JSON
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if ("thread_num".equals(name)) { // we only care about the thread number, everything else we have
                            return new ThreadLink(source.boardCode, reader.nextInt(), source.postId);
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } catch (Exception ignored) {}
                return null;
            }
        };
    }

    @Override
    public ArchiveEndpoints endpoints() {
        return new ArchiveEndpoints() {
            @Override
            public HttpUrl thread(Loadable loadable) {
                return new HttpUrl.Builder()
                        .scheme("https")
                        .host(domain)
                        .addPathSegment("_")
                        .addPathSegment("api")
                        .addPathSegment("chan")
                        .addPathSegment("thread")
                        .addQueryParameter("board", loadable.boardCode)
                        .addQueryParameter("num", String.valueOf(loadable.no))
                        .build();
            }

            @Override
            public HttpUrl resolvePost(String boardCode, int postNo) {
                return new HttpUrl.Builder()
                        .scheme("https")
                        .host(domain)
                        .addPathSegment("_")
                        .addPathSegment("api")
                        .addPathSegment("chan")
                        .addPathSegment("post")
                        .addQueryParameter("board", boardCode)
                        .addQueryParameter("num", String.valueOf(postNo))
                        .build();
            }
        };
    }

    @Override
    public ExternalArchiveSiteContentReader chanReader() {
        if (reader == null) {
            reader = new FoolFuukaReader();
        }
        return reader;
    }
}
