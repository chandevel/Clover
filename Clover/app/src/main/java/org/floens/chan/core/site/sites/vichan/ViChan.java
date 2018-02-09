package org.floens.chan.core.site.sites.vichan;

import android.support.annotation.Nullable;
import android.util.JsonReader;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostHttpIcon;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;
import org.floens.chan.core.site.parser.ChanReaderProcessingQueue;
import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.core.site.parser.StyleRule;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;
import static org.floens.chan.core.site.SiteEndpoints.makeArgument;

public class ViChan extends CommonSite {
    public static final CommonSiteUrlHandler RESOLVABLE = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return ViChan.class;
        }

        @Override
        public boolean matchesName(String value) {
            return value.equals("8chan") || value.equals("8ch");
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            return url.host().equals("8ch.net");
        }

        @Override
        public Loadable resolveLoadable(Site site, HttpUrl url) {
            Matcher board = Pattern.compile("/(\\w+)")
                    .matcher(url.encodedPath());
            Matcher thread = Pattern.compile("/(\\w+)/res/(\\d+).html")
                    .matcher(url.encodedPath());

            try {
                if (thread.find()) {
                    Board b = site.board(thread.group(1));
                    if (b == null) {
                        return null;
                    }
                    Loadable l = Loadable.forThread(site, b, Integer.parseInt(thread.group(3)));

                    if (isEmpty(url.fragment())) {
                        l.markedNo = Integer.parseInt(url.fragment());
                    }

                    return l;
                } else if (board.find()) {
                    Board b = site.board(board.group(1));
                    if (b == null) {
                        return null;
                    }

                    return Loadable.forCatalog(b);
                }
            } catch (NumberFormatException ignored) {
            }

            return null;
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return "https://8ch.net/" + loadable.boardCode;
            } else if (loadable.isThreadMode()) {
                return "https://8ch.net/" + loadable.boardCode + "/res/" + loadable.no + ".html";
            } else {
                return "https://8ch.net/";
            }
        }
    };

    private static final String TAG = "ViChan";

    @Override
    public void setup() {
        setName("8chan");
        setIcon(SiteIcon.fromAssets("icons/8chan.png"));
        setBoardsType(BoardsType.INFINITE);

        setResolvable(RESOLVABLE);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new CommonEndpoints() {
            private final SimpleHttpUrl root = from("https://8ch.net");
            private final SimpleHttpUrl sys = from("https://sys.8ch.net");

            @Override
            public HttpUrl catalog(Board board) {
                return root.builder().s(board.code).s("catalog.json").url();
            }

            @Override
            public HttpUrl thread(Board board, Loadable loadable) {
                return root.builder().s(board.code).s("res").s(loadable.no + ".json").url();
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return root.builder().s("file_store").s(arg.get("tim") + "." + arg.get("ext")).url();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                String ext;
                switch (arg.get("ext")) {
                    case "jpeg":
                    case "jpg":
                    case "png":
                    case "gif":
                        ext = arg.get("ext");
                        break;
                    default:
                        ext = "jpg";
                        break;
                }

                return root.builder().s("file_store").s("thumb").s(arg.get("tim") + "." + ext).url();
            }

            @Override
            public HttpUrl icon(Post.Builder post, String icon, Map<String, String> arg) {
                SimpleHttpUrl stat = root.builder().s("static");

                if (icon.equals("country")) {
                    stat.s("flags").s(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".png");
                }

                return stat.url();
            }

            @Override
            public HttpUrl reply(Loadable loadable) {
                return sys.builder().s("post.php").url();
            }

        });

        setActions(new CommonActions() {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                call.parameter("board", reply.loadable.board.code);

                if (reply.loadable.isThreadMode()) {
                    call.parameter("post", "New Reply");
                    call.parameter("thread", String.valueOf(reply.loadable.no));
                } else {
                    call.parameter("post", "New Thread");
                    call.parameter("page", "1");
                }

                call.parameter("pwd", reply.password);
                call.parameter("name", reply.name);
                call.parameter("email", reply.options);

                if (!reply.loadable.isThreadMode() && !isEmpty(reply.subject)) {
                    call.parameter("subject", reply.subject);
                }

                call.parameter("body", reply.comment);

                if (reply.file != null) {
                    call.fileParameter("file", reply.fileName, reply.file);
                }

                if (reply.spoilerImage) {
                    call.parameter("spoiler", "on");
                }
            }

            @Override
            public void handlePost(ReplyResponse replyResponse, Response response, String result) {
                Matcher auth = Pattern.compile(".*\"captcha\": ?true.*").matcher(result);
                Matcher err = Pattern.compile(".*<h1>Error</h1>.*<h2[^>]*>(.*?)</h2>.*").matcher(result);
                if (auth.find()) {
                    replyResponse.requireAuthentication = true;
                    replyResponse.errorMessage = result;
                } else if (err.find()) {
                    replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
                } else {
                    HttpUrl url = response.request().url();
                    Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+).html").matcher(url.encodedPath());
                    try {
                        if (m.find()) {
                            replyResponse.threadNo = Integer.parseInt(m.group(1));
                            replyResponse.postNo = Integer.parseInt(url.encodedFragment());
                            replyResponse.posted = true;
                        }
                    } catch (NumberFormatException ignored) {
                        replyResponse.errorMessage = "Error posting: could not find posted thread.";
                    }
                }
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl("https://8ch.net/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post");
            }
        });

        setApi(new ViChanApi());

        CommentParser commentParser = new CommentParser();
        commentParser.addDefaultRules();
        commentParser.setQuotePattern(Pattern.compile(".*#(\\d+)"));
        commentParser.setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)"));
        commentParser.rule(StyleRule.tagRule("p").cssClass("quote").color(StyleRule.Color.INLINE_QUOTE).linkify());

        setParser(commentParser);
    }

    private class ViChanApi extends CommonApi {
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

            SiteEndpoints endpoints = queue.getLoadable().getSite().endpoints();

            // File
            String fileId = null;
            String fileExt = null;
            int fileWidth = 0;
            int fileHeight = 0;
            long fileSize = 0;
            boolean fileSpoiler = false;
            String fileName = null;

            List<PostImage> files = new ArrayList<>();

            // Country flag
            String countryCode = null;
            String trollCountryCode = null;
            String countryName = null;

            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();

                switch (key) {
                    case "no":
                        builder.id(reader.nextInt());
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
            if (fileId != null && fileName != null && fileExt != null) {
                Map<String, String> args = makeArgument("tim", fileId,
                        "ext", fileExt);
                PostImage image = new PostImage.Builder()
                        .originalName(String.valueOf(fileId))
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

            String fileExt = null;
            int fileWidth = 0;
            int fileHeight = 0;
            boolean fileSpoiler = false;
            String fileName = null;

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
                    default:
                        reader.skipValue();
                        break;
                }
            }

            reader.endObject();

            if (fileId != null && fileName != null && fileExt != null) {
                Map<String, String> args = makeArgument("tim", fileId,
                        "ext", fileExt);
                return new PostImage.Builder()
                        .originalName(String.valueOf(fileId))
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
}
