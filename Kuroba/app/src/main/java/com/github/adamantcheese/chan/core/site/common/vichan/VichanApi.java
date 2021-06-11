package com.github.adamantcheese.chan.core.site.common.vichan;

import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteEndpoints.ICON_TYPE;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ThreadNoTimeModPair;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;

import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.site.SiteEndpoints.makeArgument;

public class VichanApi
        extends CommonSite.CommonApi {
    public VichanApi(CommonSite commonSite) {
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
        PageRepository.addPages(queue.loadable.board, readCatalogWithPages(reader, queue));
    }

    public ChanPages readCatalogWithPages(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception {
        ChanPages pages = new ChanPages();
        reader.beginArray(); // Array of pages

        while (reader.hasNext()) {
            reader.beginObject(); // Page object

            int page = -1;
            List<ThreadNoTimeModPair> threads = new ArrayList<>();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "threads":
                        reader.beginArray(); // Threads array

                        while (reader.hasNext()) {
                            Pair<Integer, Long> result = readPostObjectWithReturn(reader, queue);
                            threads.add(new ThreadNoTimeModPair(result.first, result.second));
                        }

                        reader.endArray();
                        break;
                    case "page":
                        page = reader.nextInt() + 1;
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }

            if (page != -1) {
                pages.add(new ChanPage(page, threads));
            }

            reader.endObject();
        }

        reader.endArray();
        return pages;
    }

    @Override
    public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue)
            throws Exception {
        readPostObjectWithReturn(reader, queue); // ignore return for non-page requests (ie threads)
    }

    @NonNull
    private Pair<Integer, Long> readPostObjectWithReturn(JsonReader reader, ChanReaderProcessingQueue queue)
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
                case "last_modified":
                    builder.lastModified(reader.nextLong());
                    break;
                case "id":
                    builder.posterId(reader.nextString());
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
        if (fileId != null && fileName != null && fileExt != null) {
            Map<String, String> args = makeArgument("tim", fileId, "ext", fileExt);
            PostImage image = new PostImage.Builder().serverFilename(fileId)
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
            return new Pair<>(builder.no, builder.lastModified); // this return is only used for pages!
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
        return new Pair<>(builder.no, builder.lastModified); // this return is only used for pages!
    }

    private PostImage readPostImage(JsonReader reader, Post.Builder builder, SiteEndpoints endpoints)
            throws IOException {
        try {
            reader.beginObject();
        } catch (Exception e) {
            //workaround for weird 8chan error where extra_files has a random empty array in it
            reader.beginArray();
            reader.endArray();
            try {
                reader.beginObject();
            } catch (Exception e1) {
                return null;
            }
        }

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
