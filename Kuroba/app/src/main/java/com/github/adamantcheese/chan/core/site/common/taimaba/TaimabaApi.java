package com.github.adamantcheese.chan.core.site.common.taimaba;

import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteEndpoints.ICON_TYPE;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;

import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.site.SiteEndpoints.makeArgument;

public class TaimabaApi
        extends CommonSite.CommonApi {
    public TaimabaApi(CommonSite commonSite) {
        super(commonSite);
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
        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        long fileSize = 0;
        boolean fileSpoiler = false;
        String fileName = null;

        /* prevent API parse error
           resto is not available on opening board overview the first time
           so, we manually set the opId to 0, builder.op to true and builder.opId to 0 */
        int opId;
        builder.op(true);
        builder.opId(0);

        String postcom;

        List<PostImage> files = new ArrayList<>();

        // Country flag
        String countryCode = null;
        String countryDescription = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "no":
                    builder.no(reader.nextInt());
                    break;
                case "resto":
                    opId = reader.nextInt();
                    builder.op(opId == 0);
                    builder.opId(opId);
                    break;
                case "sticky":
                    builder.sticky(reader.nextInt() == 1);
                    break;
                case "closed":
                    builder.closed(reader.nextInt() == 1);
                    break;
                case "time":
                    builder.setUnixTimestampSeconds(reader.nextLong());
                    break;
                case "name":
                    builder.name(reader.nextString());
                    break;
                case "trip":
                    builder.tripcode(reader.nextString());
                    break;
                case "id":
                    builder.posterId(reader.nextString());
                    break;
                case "sub":
                    builder.subject(reader.nextString());
                    break;
                case "com":
                    postcom = reader.nextString();
                    postcom = postcom.replaceAll(">(.*+)", "<blockquote class=\"unkfunc\">&gt;$1</blockquote>");
                    postcom = postcom.replaceAll("<blockquote class=\"unkfunc\">&gt;>(\\d+)</blockquote>",
                            "<a href=\"#$1\">&gt;&gt;$1</a>"
                    );
                    postcom = postcom.replaceAll("\n", "<br/>");
                    postcom = postcom.replaceAll("(?i)\\[b](.*?)\\[/b]", "<b>$1</b>");
                    postcom = postcom.replaceAll("(?i)\\[\\*\\*](.*?)\\[/\\*\\*]", "<b>$1</b>");
                    postcom = postcom.replaceAll("(?i)\\[i](.*?)\\[/i]", "<i>$1</i>");
                    postcom = postcom.replaceAll("(?i)\\[\\*](.*?)\\[/\\*]", "<i>$1</i>");
                    postcom =
                            postcom.replaceAll("(?i)\\[spoiler](.*?)\\[/spoiler]", "<span class=\"spoiler\">$1</span>");
                    postcom = postcom.replaceAll("(?i)\\[%](.*?)\\[/%]", "<span class=\"spoiler\">$1</span>");
                    postcom = postcom.replaceAll("(?i)\\[s](.*?)\\[/s]", "<strike>$1</strike>");
                    postcom = postcom.replaceAll("(?i)\\[pre](.*?)\\[/pre]", "<pre>$1</pre>");
                    postcom = postcom.replaceAll("(?i)\\[sub](.*?)\\[/sub]", "<pre>$1</pre>");
                    builder.comment(postcom);
                    break;
                case "filename":
                    fileName = reader.nextString();
                    break;
                case "ext":
                    fileExt = reader.nextString().replace(".", "");
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
                case "country":
                    countryCode = reader.nextString();
                    break;
                case "country_name":
                    countryDescription = reader.nextString();
                    break;
                case "spoiler":
                    fileSpoiler = reader.nextInt() == 1;
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
                case "capcode":
                    builder.moderatorCapcode(reader.nextString());
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
                default:
                    // Unknown/ignored key
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        // The file from between the other values.
        if (fileName != null && fileExt != null) {
            Map<String, String> args = makeArgument("tim", fileName, "ext", fileExt);
            PostImage image = new PostImage.Builder().thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
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
            // Id is known, use the cached post object.
            queue.addForReuse(cached);
            return;
        }

        if (countryCode != null && countryDescription != null) {
            Pair<HttpUrl, PassthroughBitmapResult> resultPair =
                    endpoints.icon(ICON_TYPE.COUNTRY_FLAG, makeArgument("country_code", countryCode));
            builder.addHttpIcon(new PostHttpIcon(ICON_TYPE.COUNTRY_FLAG,
                    resultPair.first,
                    resultPair.second,
                    countryCode,
                    countryDescription
            ));
        }

        queue.addForParse(builder);
    }

    private PostImage readPostImage(
            JsonReader reader, Post.Builder builder, SiteEndpoints endpoints
    )
            throws IOException {
        reader.beginObject();

        long fileSize = 0;
        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        boolean fileSpoiler = false;
        String fileName = null;

        while (reader.hasNext()) {
            switch (reader.nextName()) {
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
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        if (fileName != null && fileExt != null) {
            Map<String, String> args = makeArgument("tim", fileName, "ext", fileExt);
            return new PostImage.Builder().thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
                    .spoilerThumbnailUrl(endpoints.thumbnailUrl(builder, true, args))
                    .imageUrl(endpoints.imageUrl(builder, args))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .build();
        }
        return null;
    }
}
