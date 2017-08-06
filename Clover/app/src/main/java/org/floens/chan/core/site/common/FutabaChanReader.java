package org.floens.chan.core.site.common;


import android.util.JsonReader;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostHttpIcon;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.site.SiteEndpoints;
import org.jsoup.parser.Parser;

import java.util.HashMap;
import java.util.Map;

import okhttp3.HttpUrl;

public class FutabaChanReader implements ChanReader {
    @Override
    public void loadThread(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
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
    public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
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
    public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue) throws Exception {
        Post.Builder builder = new Post.Builder();
        builder.board(queue.getLoadable().board);

        // File
        String fileId = null;
        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        long fileSize = 0;
        boolean fileSpoiler = false;
        String fileName = null;

        // Country flag
        String countryCode = null;
        String trollCountryCode = null;
        String countryName = null;

        // 4chan pass leaf
        int since4pass = 0;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "no":
                    builder.id(reader.nextInt());
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
                case "troll_country":
                    trollCountryCode = reader.nextString();
                    break;
                case "country_name":
                    countryName = reader.nextString();
                    break;
                case "spoiler":
                    fileSpoiler = reader.nextInt() == 1;
                    break;
                case "resto":
                    int opId = reader.nextInt();
                    builder.op(opId == 0);
                    builder.opId(opId);
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
                case "id":
                    builder.posterId(reader.nextString());
                    break;
                case "capcode":
                    builder.moderatorCapcode(reader.nextString());
                    break;
                case "since4pass":
                    since4pass = reader.nextInt();
                    break;
                default:
                    // Unknown/ignored key
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (builder.op) {
            // Update OP fields later on the main thread
            Post.Builder op = new Post.Builder();
            op.closed(builder.closed);
            op.archived(builder.archived);
            op.sticky(builder.sticky);
            op.replies(builder.replies);
            op.images(builder.images);
            op.uniqueIps(builder.uniqueIps);
            queue.setOp(op);
        }

        Post cached = queue.getCachedPost(builder.id);
        if (cached != null) {
            // Id is known, use the cached post object.
            queue.addForReuse(cached);
            return;
        }

        SiteEndpoints endpoints = queue.getLoadable().getSite().endpoints();
        if (fileId != null && fileName != null && fileExt != null) {
            Map<String, String> hack = new HashMap<>(2);
            hack.put("tim", fileId);
            hack.put("ext", fileExt);
            builder.image(new PostImage.Builder()
                    .originalName(String.valueOf(fileId))
                    .thumbnailUrl(endpoints.thumbnailUrl(builder, fileSpoiler, hack))
                    .imageUrl(endpoints.imageUrl(builder, hack))
                    .filename(Parser.unescapeEntities(fileName, false))
                    .extension(fileExt)
                    .imageWidth(fileWidth)
                    .imageHeight(fileHeight)
                    .spoiler(fileSpoiler)
                    .size(fileSize)
                    .build());
        }

        if (countryCode != null && countryName != null) {
            Map<String, String> arg = new HashMap<>(1);
            arg.put("country_code", countryCode);
            HttpUrl countryUrl = endpoints.icon(builder, "country", arg);
            builder.addHttpIcon(new PostHttpIcon(countryUrl, countryName));
        }

        if (trollCountryCode != null && countryName != null) {
            Map<String, String> arg = new HashMap<>(1);
            arg.put("troll_country_code", trollCountryCode);
            HttpUrl countryUrl = endpoints.icon(builder, "troll_country", arg);
            builder.addHttpIcon(new PostHttpIcon(countryUrl, countryName));
        }

        if (since4pass != 0) {
            HttpUrl iconUrl = endpoints.icon(builder, "since4pass", null);
            builder.addHttpIcon(new PostHttpIcon(iconUrl, String.valueOf(since4pass)));
        }

        queue.addForParse(builder);
    }
}
