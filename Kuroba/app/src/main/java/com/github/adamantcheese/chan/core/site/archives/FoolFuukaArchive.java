package com.github.adamantcheese.chan.core.site.archives;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.CommentParser.ThreadLink;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.google.common.io.Files;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

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
            extends ExternalArchiveChanReader {

        private PostParser parser;

        @Override
        public PostParser getParser() {
            if (parser == null) {
                parser = new DefaultPostParser(new FoolFuukaCommentParser(domain));
            }
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
                        queue.setOp(builder);
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

                        if (builder.op && TextUtils.isEmpty(builder.subject)) {
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
                                    imageBuilder.spoilerThumbnailUrl(HttpUrl.get(
                                            BuildConfig.RESOURCES_ENDPOINT + "default_spoiler.png"));
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
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                    } else {
                                        String value = reader.nextString();
                                        try {
                                            imageBuilder.imageUrl(HttpUrl.get(value));
                                        } catch (Exception e) {
                                            // some weird archives use this one
                                            try {
                                                imageBuilder.imageUrl(HttpUrl.get("https://" + domain + value));
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                    break;
                                case "media_link":
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                        if (imageBuilder.hasImageUrl()) break;
                                        imageBuilder.imageUrl(HttpUrl.get(
                                                BuildConfig.RESOURCES_ENDPOINT + "archive_missing.png"));
                                    } else {
                                        imageBuilder.imageUrl(HttpUrl.get(reader.nextString()));
                                    }
                                    break;
                                case "media_orig":
                                    imageBuilder.serverFilename(Files.getNameWithoutExtension(reader.nextString()));
                                    break;
                                case "thumb_link":
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                        imageBuilder.thumbnailUrl(HttpUrl.get(
                                                BuildConfig.RESOURCES_ENDPOINT + "archive_missing.png"));
                                    } else {
                                        imageBuilder.thumbnailUrl(HttpUrl.get(reader.nextString()));
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

    private static class FoolFuukaCommentParser
            extends CommentParser {
        private final String domain;

        public FoolFuukaCommentParser(String domain) {
            super();
            this.domain = domain;
            addDefaultRules();
            // matches https://domain.tld/boardcode/blah/opNo(/#p)postNo/
            // blah can be "thread" or "post"; "thread" is just a normal thread link, but "post" is a crossthread link that needs to be resolved
            Pattern compiledPattern = Pattern.compile(
                    "(?:https://)?" + domain.replaceAll("\\.", "\\\\.") + "/(.*?)/(?:.*?)/(\\d*+)/?#?p?(\\d+)?/?");
            setQuotePattern(compiledPattern);
            // note that if an archive does NOT support a board, it will not match this as the archiver leaves things as-as
            setFullQuotePattern(compiledPattern);
        }

        @Override
        public CharSequence handleTag(
                PostParser.Callback callback,
                @NonNull Theme theme,
                Post.Builder post,
                String tag,
                CharSequence text,
                Element element
        ) {
            // for some reason, stuff is wrapped in a "greentext" span if it starts with a > regardless of it is greentext or not
            // the default post parser has already handled any inner tags by the time that it has gotten to this case, since
            // deepest nodes are processed first
            // in this case, we just want to return the text that has already been processed inside of this "greentext" node
            // otherwise duplicate PostLinkables will be generated
            if (element.getElementsByTag("span").hasClass("greentext") && element.childrenSize() > 0) {
                return text;
            }
            return super.handleTag(callback, theme, post, tag, text, element);
        }

        @Override
        public String createQuoteElementString(Post.Builder post) {
            return "<span class=\"greentext\"><a href=\"https://" + domain + "/" + post.board.code + "/thread/"
                    + post.opId + "/#$1\">&gt;&gt;$1</a></span>";
        }
    }

    @Override
    public ArchiveSiteUrlHandler resolvable() {
        return new ArchiveSiteUrlHandler() {
            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                if (loadable.isThreadMode()) {
                    return "https://" + domain + "/" + loadable.boardCode + "/thread/" + loadable.no + (postNo > 0 ? "#"
                            + postNo : "");
                } else {
                    return "https://" + domain + "/" + loadable.boardCode;
                }
            }

            @Override
            public Loadable resolveLoadable(Site site, HttpUrl url) {
                List<String> parts = url.pathSegments();

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
                            return new ThreadLink(source.board.code, reader.nextInt(), source.postId);
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
                return HttpUrl.get("https://" + domain + "/_/api/chan/thread/?board=" + loadable.boardCode + "&num="
                        + loadable.no);
            }

            @Override
            public HttpUrl resolvePost(String boardCode, int postNo) {
                return HttpUrl.get("https://" + domain + "/_/api/chan/post/?board=" + boardCode + "&num=" + postNo);
            }
        };
    }

    @Override
    public ExternalArchiveChanReader chanReader() {
        if (reader == null) {
            reader = new FoolFuukaReader();
        }
        return reader;
    }
}
