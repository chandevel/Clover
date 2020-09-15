package com.github.adamantcheese.chan.core.site;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
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

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class FoolFuukaArchive {
    private String domain;
    private String name;
    private List<String> boardCodes;

    public FoolFuukaArchive(ArchivesManager.Archives archives) {
        domain = archives.domain;
        name = archives.name;
        boardCodes = archives.boards;
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
                (NetUtils.JsonParser<ChanLoaderResponse>) reader -> new ChanReaderParser(Loadable.forThread(board,
                        opNo,
                        "",
                        false
                ), new ArrayList<>(), new FoolFuukaReader()).parse(reader)
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
            reader.beginObject();
            reader.nextString();
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
            reader.nextName(); // op or post number; not necessary as it's in the rest of the data
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                switch (key) {
                    case "num":
                        builder.id(reader.nextInt());
                        break;
                    case "op":
                        builder.op(reader.nextInt() == 1);
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
                        builder.tripcode(reader.nextString());
                        break;
                    case "title_processed":
                        if (builder.op) {
                            queue.getLoadable().title = reader.nextString();
                        } else {
                            reader.skipValue();
                        }
                        break;
                    case "comment_processed":
                        String comment = reader.nextString();

                        break;
                    case "sticky":
                        boolean sticky = reader.nextInt() == 1;
                        break;
                    case "locked":
                        boolean locked = reader.nextInt() == 1;
                        break;
                    case "deleted":
                        boolean deleted = reader.nextInt() == 1;
                        break;
                    case "media":
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String mediaKey = reader.nextName();
                            switch (mediaKey) {
                                case "spoiler":
                                    boolean spoiler = reader.nextInt() == 1;
                                    break;
                                case "media_w":
                                    int width = reader.nextInt();
                                    break;
                                case "media_h":
                                    int height = reader.nextInt();
                                    break;
                                case "media_size":
                                    long size = reader.nextLong();
                                    break;
                                case "media_hash":
                                    String hash = reader.nextString();
                                    break;
                                case "media_link":
                                    HttpUrl fullsize = HttpUrl.get(reader.nextString());
                                    break;
                                case "thumb_link":
                                    HttpUrl thumbnail = HttpUrl.get(reader.nextString());
                                    break;
                                default:
                                    reader.skipValue();
                            }
                        }
                        reader.endObject();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();

            if (builder.op) {
                // Update OP fields later on the main thread
                builder.closed(builder.closed);
                builder.archived(builder.archived);
                builder.sticky(builder.sticky);
                queue.setOp(builder);
            }

            queue.addForParse(builder);
        }
    }
}
