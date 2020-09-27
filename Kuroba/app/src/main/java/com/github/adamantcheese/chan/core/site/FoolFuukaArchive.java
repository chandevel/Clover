package com.github.adamantcheese.chan.core.site;

import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class FoolFuukaArchive
        extends Archive {

    private FoolFuukaReader reader;
    private FoolFuukaCommentParser parser;

    public FoolFuukaArchive(String domain, String name, List<String> boardCodes, boolean searchEnabled) {
        super(domain, name, boardCodes, searchEnabled);
        reader = new FoolFuukaReader();
        parser = new FoolFuukaCommentParser();
    }

    private class FoolFuukaReader
            implements ChanReader {

        @Override
        public PostParser getParser() {
            return new DefaultPostParser(parser);
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

            reader.nextName(); // posts object
            reader.beginObject();
            // Posts object
            while (reader.hasNext()) {
                readPostObject(reader, queue);
            }
            reader.endObject();
            reader.endObject();
            reader.endObject();
        }

        @Override
        public void loadCatalog(
                JsonReader reader, ChanReaderProcessingQueue queue
        ) {} // archives don't support catalogs

        @Override
        public void readPostObject(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            Post.Builder builder = new Post.Builder();
            builder.board(queue.getLoadable().board);
            reader.nextName(); // "op" or post number; not necessary as it's in the rest of the data so ignore this
            reader.beginObject(); // post object itself
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "num":
                        builder.id(reader.nextInt());
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
                            queue.getLoadable().title = title;
                        } else {
                            reader.skipValue();
                        }
                        break;
                    case "comment":
                        String comment = "";
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull();
                        } else {
                            comment = reader.nextString();
                            comment = comment.replaceAll(">>(\\d+)",
                                    "<a href=\"#$1\" class=\"quotelink\">&gt;&gt;$1</a>"
                            );
                            comment = comment.replaceAll("\n>(.*)", "<br><span class=\"quote\">&gt;$1</span>");
                            comment = comment.replaceAll("\n", "<br>");
                            builder.comment(comment);
                        }

                        if (builder.op && TextUtils.isEmpty(builder.subject)) {
                            if (!TextUtils.isEmpty(comment)) {
                                queue.getLoadable().title =
                                        comment.subSequence(0, Math.min(comment.length(), 200)) + "";
                            } else {
                                queue.getLoadable().title = "/" + builder.board.code + "/" + builder.opId;
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
                                    imageBuilder.filename(StringUtils.removeExtensionFromFileName(filename));
                                    imageBuilder.extension(StringUtils.extractFileNameExtension(filename));
                                    break;
                                case "media_hash":
                                    imageBuilder.fileHash(reader.nextString(), true);
                                    break;
                                case "media_link":
                                    imageBuilder.imageUrl(HttpUrl.get(reader.nextString()));
                                    break;
                                case "media_orig":
                                    imageBuilder.serverFilename(StringUtils.removeExtensionFromFileName(reader.nextString()));
                                    break;
                                case "thumb_link":
                                    imageBuilder.thumbnailUrl(HttpUrl.get(reader.nextString()));
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
        public FoolFuukaCommentParser() {
            addDefaultRules();
            setQuotePattern(Pattern.compile(".*#(\\d+)"));
            setFullQuotePattern(Pattern.compile("/(\\w+)/thread/(\\d+)#(\\d+)"));
            /*
            rule(StyleRule.tagRule("strike").strikeThrough());
            rule(StyleRule.tagRule("pre").monospace().size(sp(12f)));
            rule(StyleRule.tagRule("blockquote")
                    .cssClass("unkfunc")
                   .foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE)
                    .linkify());
            */
        }
    }

    @Override
    public ArchiveSiteUrlHandler resolvable() {
        return new ArchiveSiteUrlHandler() {

            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                return "https://" + domain + "/" + loadable.boardCode + "/thread/" + loadable.no + "#" + postNo;
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
        };
    }

    @Override
    public ChanReader chanReader() {
        return reader;
    }
}
