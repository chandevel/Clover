package com.github.adamantcheese.chan.core.site.archives;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Element;

import java.util.List;

import kotlin.NotImplementedError;
import okhttp3.HttpUrl;

public class AyaseArchive
        extends ExternalSiteArchive {
    private AyaseReader reader;

    public AyaseArchive(String domain, String name, List<String> boardCodes, boolean searchEnabled) {
        super(domain, name, boardCodes, searchEnabled);
    }

    /*

        ----------------------------    NOTE    ----------------------------

        Pretty much everything in here is currently "unimplemented" and will throw exceptions; there aren't really any
        sites that use this archive yet so I haven't written the parsing for them. The parsing should end up looking
        pretty similar to Chan4's though.

        Commented out code is currently taken from FoolFuukaArchive as an example; don't use as-is, it won't work.

     */

    private class AyaseReader
            extends ExternalArchiveChanReader {

        private PostParser parser;

        @Override
        public PostParser getParser() {
            if (parser == null) {
                parser = new DefaultPostParser(new AyaseCommentParser(domain));
            }
            return parser;
        }

        @Override
        public void loadThread(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            throw new NotImplementedError();
            /*reader.beginObject(); // start JSON
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
            reader.endObject();*/
        }

        @Override
        public void readPostObject(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            Post.Builder builder = new Post.Builder();
            builder.board(queue.loadable.board);
            throw new NotImplementedError();
            /*reader.nextName(); // "op" or post number; not necessary as it's in the rest of the data so ignore this
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
                            queue.getLoadable().title = title;
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
                                    imageBuilder.filename(StringUtils.removeExtensionFromFileName(filename));
                                    imageBuilder.extension(StringUtils.extractFileNameExtension(filename));
                                    break;
                                case "media_hash":
                                    imageBuilder.fileHash(reader.nextString(), true);
                                    break;
                                case "media_link":
                                    if (reader.peek() == JsonToken.NULL) {
                                        reader.nextNull();
                                        imageBuilder.imageUrl(HttpUrl.get(
                                                BuildConfig.RESOURCES_ENDPOINT + "archive_missing.png"));
                                    } else {
                                        imageBuilder.imageUrl(HttpUrl.get(reader.nextString()));
                                    }
                                    break;
                                case "media_orig":
                                    imageBuilder.serverFilename(StringUtils.removeExtensionFromFileName(reader.nextString()));
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

            queue.addForParse(builder);*/
        }
    }

    private static class AyaseCommentParser
            extends CommentParser {
        public AyaseCommentParser(String domain) {
            super();
            addDefaultRules();
            throw new NotImplementedError();
            /*// matches https://domain.tld/boardcode/blah/opNo(/#p)postNo/
            // blah can be "thread" or "post"; "thread" is just a normal thread link, but "post" is a crossthread link that needs to be resolved
            Pattern compiledPattern = Pattern.compile(
                    "(?:https://)?" + domain.replaceAll("\\.", "\\\\.") + "/(.*?)/(?:.*?)/(\\d*+)/?#?p?(\\d+)?/?");
            setQuotePattern(compiledPattern);
            // note that if an archive does NOT support a board, it will not match this as the archiver leaves things as-as
            setFullQuotePattern(compiledPattern);*/
        }

        @Override
        public CharSequence handleTag(
                PostParser.Callback callback,
                Theme theme,
                Post.Builder post,
                String tag,
                CharSequence text,
                Element element
        ) {
            throw new NotImplementedError(); // this likely can be fully removed and not overridden
            /*// for some reason, stuff is wrapped in a "greentext" span if it starts with a > regardless of it is greentext or not
            // the default post parser has already handled any inner tags by the time that it has gotten to this case, since
            // deepest nodes are processed first
            // in this case, we just want to return the text that has already been processed inside of this "greentext" node
            // otherwise duplicate PostLinkables will be generated
            if (element.getElementsByTag("span").hasClass("greentext") && element.childrenSize() > 0) {
                return text;
            }
            return super.handleTag(callback, theme, post, tag, text, element);*/
        }

        @Override
        public String createQuoteElementString(Post.Builder post) {
            return "$0"; // return the input
        }
    }

    @Override
    public ArchiveSiteUrlHandler resolvable() {
        return new ArchiveSiteUrlHandler() {
            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                throw new NotImplementedError();
                /*if (loadable.isThreadMode()) {
                    return "https://" + domain + "/" + loadable.boardCode + "/thread/" + loadable.no + (postNo > 0 ? "#"
                            + postNo : "");
                } else {
                    return "https://" + domain + "/" + loadable.boardCode;
                }*/
            }

            @Override
            public CommentParser.ThreadLink resolveToThreadLink(CommentParser.ResolveLink source, JsonReader reader) {
                throw new NotImplementedError();
                /*try {
                    reader.beginObject(); //begin JSON
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if ("thread_num".equals(name)) { // we only care about the thread number, everything else we have
                            return new CommentParser.ThreadLink(source.board.code, reader.nextInt(), source.postId);
                        } else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } catch (Exception ignored) {}
                return null;*/
            }
        };
    }

    @Override
    public ArchiveEndpoints endpoints() {
        return new ArchiveEndpoints() {
            @Override
            public HttpUrl thread(Loadable loadable) {
                throw new NotImplementedError();
                //return HttpUrl.get("https://" + domain + "/_/api/chan/thread/?board=" + loadable.boardCode + "&num="+ loadable.no);
            }

            @Override
            public HttpUrl resolvePost(String boardCode, int postNo) {
                throw new NotImplementedError();
                //return HttpUrl.get("https://" + domain + "/_/api/chan/post/?board=" + boardCode + "&num=" + postNo);
            }
        };
    }

    @Override
    public ExternalArchiveChanReader chanReader() {
        if (reader == null) {
            reader = new AyaseReader();
        }
        return reader;
    }
}
