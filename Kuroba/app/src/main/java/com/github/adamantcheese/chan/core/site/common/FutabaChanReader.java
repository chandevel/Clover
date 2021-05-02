package com.github.adamantcheese.chan.core.site.common;

import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteEndpoints.ICON_TYPE;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;

import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import static com.github.adamantcheese.chan.core.site.SiteEndpoints.makeArgument;

public class FutabaChanReader
        implements ChanReader {
    private final PostParser postParser;

    public FutabaChanReader() {
        CommentParser commentParser = new CommentParser().addDefaultRules();
        this.postParser = new DefaultPostParser(commentParser);
    }

    @Override
    public PostParser getParser() {
        return postParser;
    }

    @Override
    public void loadThread(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception {
        reader.beginObject();
        // Page object
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("posts")) {
                reader.beginArray();
                // Thread array
                while (reader.hasNext()) {
                    // Thread object
                    readPostObject(reader, queue);
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    @Override
    public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception {
        reader.beginArray(); // Array of pages

        while (reader.hasNext()) {
            reader.beginObject(); // Page object

            while (reader.hasNext()) {
                if (reader.nextName().equals("threads")) {
                    reader.beginArray(); // Threads array

                    while (reader.hasNext()) {
                        readPostObject(reader, queue);
                    }

                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
        }

        reader.endArray();
    }

    @Override
    public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception {
        Post.Builder builder = new Post.Builder();
        builder.board(queue.loadable.board);

        SiteEndpoints endpoints = queue.loadable.site.endpoints();

        // File
        String fileId = null;
        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        long fileSize = 0;
        boolean fileSpoiler = false;
        String fileName = null;
        String fileHash = null;
        boolean fileDeleted = false;

        List<PostImage> files = new ArrayList<>();

        // Country flag
        String countryCode = null;
        String countryDescription = null;

        // Board flag
        String boardFlagCode = null;
        String boardFlagDescription = null;

        // 4chan pass leaf
        int since4pass = 0;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "no":
                    builder.no(reader.nextInt());
                    break;
                /*case "now":
                    post.date = reader.nextString();
                    break;*/
                case "sub":
                    builder.subject(reader.nextString());
                    break;
                case "name":
                    builder.name(reader.nextString());
                    break;
                case "com":
                    builder.comment(reader.nextString());
                    break;
                case "tim":
                    fileId = reader.nextString();
                    break;
                case "time":
                    builder.setUnixTimestampSeconds(reader.nextLong());
                    break;
                case "ext":
                    fileExt = reader.nextString().replace(".", "");
                    break;
                case "w":
                    fileWidth = reader.nextInt();
                    break;
                case "h":
                    fileHeight = reader.nextInt();
                    break;
                case "fsize":
                    fileSize = reader.nextLong();
                    break;
                case "filename":
                    fileName = reader.nextString();
                    break;
                case "trip":
                    builder.tripcode(reader.nextString());
                    break;
                case "country":
                    countryCode = reader.nextString();
                    break;
                case "country_name":
                    countryDescription = reader.nextString();
                    break;
                case "board_flag":
                    boardFlagCode = reader.nextString();
                    break;
                case "flag_name":
                    boardFlagDescription = reader.nextString();
                    break;
                case "spoiler":
                    fileSpoiler = reader.nextInt() == 1;
                    break;
                case "resto":
                    int opId = reader.nextInt();
                    builder.op(opId == 0);
                    builder.opId(opId);
                    break;
                case "filedeleted":
                    fileDeleted = reader.nextInt() == 1;
                    break;
                case "sticky":
                    builder.sticky(reader.nextInt() == 1);
                    break;
                case "closed":
                    builder.closed(reader.nextInt() == 1);
                    break;
                case "archived":
                    builder.archived(reader.nextInt() == 1);
                    break;
                case "replies":
                    builder.replies(reader.nextInt());
                    break;
                case "images":
                    builder.images(reader.nextInt());
                    break;
                case "unique_ips":
                    builder.uniqueIps(reader.nextInt());
                    break;
                case "last_modified":
                    builder.lastModified(reader.nextLong());
                    break;
                case "id":
                    builder.posterId(reader.nextString());
                    break;
                case "capcode":
                    builder.moderatorCapcode(reader.nextString());
                    break;
                case "since4pass":
                    since4pass = reader.nextInt();
                    break;
                case "extra_files":
                    reader.beginArray();

                    while (reader.hasNext()) {
                        PostImage postImage = readPostImage(reader, builder, endpoints);
                        if (postImage != null) {
                            files.add(postImage);
                        }
                    }

                    reader.endArray();
                    break;
                case "md5":
                    fileHash = reader.nextString();
                    break;
                default:
                    // Unknown/ignored key
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        // The file from between the other values.
        if ((fileId != null && fileName != null && fileExt != null) || fileDeleted) {
            // /f/ is a strange case where the actual filename is used for the file on the server
            Map<String, String> args =
                    makeArgument("tim", "f".equals(queue.loadable.boardCode) ? fileName : fileId, "ext", fileExt);
            PostImage image = new PostImage.Builder().serverFilename(fileId)
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileDeleted ? "file_deleted" : fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .fileHash(fileHash, true)
                    .deleted(fileDeleted)
                    .build();
            // Insert it at the beginning.
            files.add(0, image);
        }

        builder.images(files);

        if (builder.op) {
            // Update OP fields later on the main thread
            queue.setOp(builder.clone());
        }

        Post cached = queue.getCachedPost(builder.no);
        if (cached != null) {
            // Post no is known, use the cached post object.
            queue.addForReuse(cached);
            return;
        }

        if (countryCode != null && countryDescription != null) {
            HttpUrl countryUrl = endpoints.icon(ICON_TYPE.COUNTRY_FLAG, makeArgument("country_code", countryCode));
            builder.addHttpIcon(new PostHttpIcon(ICON_TYPE.COUNTRY_FLAG,
                    countryUrl,
                    new NetUtilsClasses.PassthroughBitmapResult(),
                    countryCode,
                    countryDescription
            ));
        }

        if (boardFlagCode != null && boardFlagDescription != null) {
            HttpUrl flagUrl = endpoints.icon(ICON_TYPE.BOARD_FLAG, makeArgument("board_code", builder.board.code));
            // note: this is bad, but once this is cached it never makes a network request and is fine afterwards
            Response flagAlignments = NetUtils.applicationClient.newCall(new Request.Builder().url(
                    "https://s.4cdn.org/image/flags/" + builder.board.code + "/flags.1.css").build()).execute();
            String alignmentsString = flagAlignments.body().string();
            Pattern dimsPattern = Pattern.compile("\\.bfl\\{.*width:(\\d+)px;height:(\\d+)px;.*\\}");
            Matcher dimMatcher = dimsPattern.matcher(alignmentsString);
            dimMatcher.find();

            Pair<Integer, Integer> dims = new Pair<>(Math.abs(Integer.parseInt(dimMatcher.group(1))),
                    Math.abs(Integer.parseInt(dimMatcher.group(2)))
            );

            Pattern flagPattern = Pattern.compile(
                    "\\.bfl-" + boardFlagCode + "\\{background-position:-?(\\d+)(?:px)? -?(\\d+)(?:px)?\\}",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher flagMatcher = flagPattern.matcher(alignmentsString);
            flagMatcher.find();

            Pair<Integer, Integer> origin = new Pair<>(Math.abs(Integer.parseInt(flagMatcher.group(1))),
                    Math.abs(Integer.parseInt(flagMatcher.group(2)))
            );

            builder.addHttpIcon(new PostHttpIcon(ICON_TYPE.BOARD_FLAG,
                    flagUrl,
                    new NetUtilsClasses.CroppingBitmapResult(origin, dims),
                    boardFlagCode,
                    boardFlagDescription
            ));
        }

        if (since4pass != 0) {
            HttpUrl iconUrl = endpoints.icon(ICON_TYPE.SINCE4PASS, null);
            builder.addHttpIcon(new PostHttpIcon(ICON_TYPE.SINCE4PASS,
                    iconUrl,
                    new NetUtilsClasses.PassthroughBitmapResult(),
                    "since4pass",
                    String.valueOf(since4pass)
            ));
        }

        queue.addForParse(builder);
    }

    private PostImage readPostImage(JsonReader reader, Post.Builder builder, SiteEndpoints endpoints)
            throws IOException {
        reader.beginObject();

        String fileId = null;
        long fileSize = 0;

        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        boolean fileSpoiler = false;
        String fileName = null;
        String fileHash = null;

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tim":
                    fileId = reader.nextString();
                    break;
                case "fsize":
                    fileSize = reader.nextLong();
                    break;
                case "w":
                    fileWidth = reader.nextInt();
                    break;
                case "h":
                    fileHeight = reader.nextInt();
                    break;
                case "spoiler":
                    fileSpoiler = reader.nextInt() == 1;
                    break;
                case "ext":
                    fileExt = reader.nextString().replace(".", "");
                    break;
                case "filename":
                    fileName = reader.nextString();
                    break;
                case "md5":
                    fileHash = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        if (fileId != null && fileName != null && fileExt != null) {
            // /f/ does not allow image posts not in the OP, so no special handling is needed here
            Map<String, String> args = makeArgument("tim", fileId, "ext", fileExt);
            return new PostImage.Builder().serverFilename(fileId)
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .fileHash(fileHash, true)
                    .build();
        }
        return null;
    }
}
