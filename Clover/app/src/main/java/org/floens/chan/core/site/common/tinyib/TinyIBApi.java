package org.floens.chan.core.site.common.tinyib;

import android.util.JsonReader;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostHttpIcon;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.parser.ChanReaderProcessingQueue;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static org.floens.chan.core.site.SiteEndpoints.makeArgument;

public class TinyIBApi extends CommonSite.CommonApi {
    public TinyIBApi(CommonSite commonSite) {
        super(commonSite);
    }

    @Override
    public void loadThread(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        reader.beginObject();
        // Page object
        while (reader.hasNext()) {
            if (reader.nextName().equals("posts")) {
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
    public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
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

    @Override
    public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        Post.Builder builder = new Post.Builder();
        builder.board(queue.getLoadable().board);

        SiteEndpoints endpoints = queue.getLoadable().getSite().endpoints();

        // File
        String fileId = null;
        int fileWidth = 0;
        int fileHeight = 0;
        long fileSize = 0;
        boolean fileSpoiler = false;
        String file = null;
        String fileName = null;
        String fileExt = null;
        String thumbnail = null;
        String path = null;
        String thumbnailpath = null;
        String originalName = null;

        /* prevent API parse error

           resto is not available on opening board overview the first time
           so, we manually set the opId to 0, builder.op to true and builder.opId to 0 */
        int opId = 0;
        builder.op(opId == 0);
        builder.opId(0);

        String postcom = null;

        List<PostImage> files = new ArrayList<>();

        // Country flag
        String countryCode = null;
        String trollCountryCode = null;
        String countryName = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "parent":
                    opId = Integer.valueOf(reader.nextString());
                    builder.op(opId == 0);
                    builder.opId(opId);
                    break;
                case "stickied":
                    builder.sticky(Integer.valueOf(reader.nextString()) == 1);
                    break;
                case "timestamp":
                    builder.setUnixTimestampSeconds(Long.parseLong(reader.nextString()));
                    break;
                case "name":
                    builder.name(reader.nextString());
                    break;
                case "tripcode":
                    builder.tripcode(reader.nextString());
                    break;
                case "id":
                    builder.id(Integer.valueOf(reader.nextString()));
                    break;
                case "subject":
                    builder.subject(reader.nextString());
                    break;
                case "message":
                    postcom = reader.nextString();
                    builder.comment(postcom);
                    break;
                case "file":
                    file = reader.nextString();
                    break;
                case "file_size":
                    fileSize = Long.parseLong(reader.nextString());
                    break;
                case "image_width":
                    fileWidth = Integer.valueOf(reader.nextString());
                    break;
                case "image_height":
                    fileHeight = Integer.valueOf(reader.nextString());
                    break;
                case "bumped":
                    builder.lastModified(Long.parseLong(reader.nextString()));
                    break;
                case "thumb":
                    thumbnail = reader.nextString();
                    break;
                case "file_original":
                    originalName = reader.nextString();
                    break;
                case "replies":
                    builder.replies(reader.nextInt());
                    break;
                case "images":
                    builder.images(reader.nextInt());
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
        if (file != null && file.length() > 0) {
            fileName = file.substring(0, file.lastIndexOf("."));
            fileExt = file.substring(file.lastIndexOf(".") + 1);
            Map<String, String> args = makeArgument("path", file,
                    "thumbnailpath", thumbnail);
            PostImage image = new PostImage.Builder()
                    .originalName(org.jsoup.parser.Parser.unescapeEntities(fileName, false))
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
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
            Post.Builder op = new Post.Builder();
            op.closed(builder.closed);
            op.archived(builder.archived);
            op.sticky(builder.sticky);
            op.replies(builder.replies);
            op.images(builder.imagesCount);
            op.uniqueIps(builder.uniqueIps);
            op.lastModified(builder.lastModified);
            queue.setOp(op);
        }

        Post cached = queue.getCachedPost(builder.id);
        if (cached != null) {
            // Id is known, use the cached post object.
            queue.addForReuse(cached);
            return;
        }

        if (countryCode != null && countryName != null) {
            HttpUrl countryUrl = endpoints.icon(builder, "country",
                    makeArgument("country_code", countryCode));
            builder.addHttpIcon(new PostHttpIcon(countryUrl, countryName));
        }

        if (trollCountryCode != null && countryName != null) {
            HttpUrl countryUrl = endpoints.icon(builder, "troll_country",
                    makeArgument("troll_country_code", trollCountryCode));
            builder.addHttpIcon(new PostHttpIcon(countryUrl, countryName));
        }

        queue.addForParse(builder);
    }

    private PostImage readPostImage(JsonReader reader, Post.Builder builder,
                                    SiteEndpoints endpoints) throws IOException {
        reader.beginObject();

        String fileId = null;
        long fileSize = 0;

        int fileWidth = 0;
        int fileHeight = 0;
        boolean fileSpoiler = false;
        String file = null;
        String fileName = null;
        String fileExt = null;
        String thumbnail = null;
        String path = null;
        String thumbnailpath = null;
        String originalName = null;

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tim":
                    fileId = reader.nextString();
                    break;
                case "file_size":
                    fileSize = Long.parseLong(reader.nextString());
                    break;
                case "image_width":
                    fileWidth = Integer.valueOf(reader.nextString());
                    break;
                case "image_height":
                    fileHeight = Integer.valueOf(reader.nextString());
                    break;
                case "spoiler":
                    fileSpoiler = Integer.valueOf(reader.nextString()) == 1;
                    break;
                case "file":
                    file = reader.nextString();
                    break;
                case "thumb":
                    thumbnail = reader.nextString();
                    break;
                case "file_original":
                    originalName = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        if (file != null && file.length() > 0) {
            fileName = file.substring(0, file.lastIndexOf("."));
            fileExt = file.substring(file.lastIndexOf(".") + 1);
            Map<String, String> args = makeArgument("path", file,
                    "thumbnailpath", thumbnail);
            return new PostImage.Builder()
                    .originalName(org.jsoup.parser.Parser.unescapeEntities(fileName, false))
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, false, args))
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