package com.github.adamantcheese.chan.core.site;

import android.util.JsonReader;
import android.util.JsonToken;

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.loader.ChanLoaderResponse;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderParser;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

public class FoolFuukaArchive {
    private String domain;
    private String name;
    private List<String> boardCodes;

    public FoolFuukaArchive(ArchivesManager.Archives archives) {
        domain = archives.domain;
        name = archives.name;
        boardCodes = archives.boardCodes;
    }

    private HttpUrl urlForThread(Board board, int opNo) {
        return HttpUrl.get("https://" + domain + "/_/api/chan/thread/?board=" + board.code + "&num=" + opNo);
    }

    public void requestArchive(Board board, int opNo) {
        NetUtils.makeJsonRequest(urlForThread(board, opNo),
                new NetUtils.JsonResult<ChanLoaderResponse>() {
                    @Override
                    public void onJsonFailure(Exception e) {

                    }

                    @Override
                    public void onJsonSuccess(ChanLoaderResponse result) {

                    }
                },
                reader -> new ChanReaderParser(Loadable.forThread(board, opNo, "", false),
                        new ArrayList<>(),
                        new FoolFuukaReader()
                ).parse(reader)
        );
    }

    private class FoolFuukaReader
            implements ChanReader {

        @Override
        public PostParser getParser() {
            CommentParser commentParser = new CommentParser();
            commentParser.addDefaultRules();
            return new DefaultPostParser(commentParser);
        }

        @Override
        public void loadThread(
                JsonReader reader, ChanReaderProcessingQueue queue
        )
                throws Exception {
            reader.beginObject(); // start JSON

            reader.nextString(); // the op number, duplicated in the post object itself
            reader.beginObject();
            // OP object
            readPostObject(reader, queue);
            reader.endObject();

            reader.beginObject();
            // Posts object
            while (reader.hasNext()) {
                readPostObject(reader, queue);
            }
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
                    case "op":
                        builder.op(reader.nextInt() == 1);
                        queue.setOp(builder);
                        break;
                    case "timestamp":
                        builder.setUnixTimestampSeconds(reader.nextLong());
                        break;
                    case "capcode":
                        builder.moderatorCapcode(reader.nextString());
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
                        comment = comment.replace("\\n", "");
                        builder.comment(comment);
                        break;
                    case "sticky":
                        builder.sticky(reader.nextInt() == 1);
                        break;
                    case "locked":
                        builder.closed(reader.nextInt() == 1);
                        break;
                    case "media":
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
                                case "media_hash":
                                    imageBuilder.fileHash(reader.nextString(), true);
                                    break;
                                case "media_link":
                                    imageBuilder.imageUrl(HttpUrl.get(reader.nextString()));
                                    break;
                                case "media_orig":
                                    imageBuilder.serverFilename(StringUtils.removeExtensionFromFileName(reader.nextString()));
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
}
