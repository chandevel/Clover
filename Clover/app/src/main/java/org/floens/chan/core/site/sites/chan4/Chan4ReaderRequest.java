/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.site.sites.chan4;

import android.util.JsonReader;

import org.floens.chan.chan.ChanLoaderRequestParams;
import org.floens.chan.chan.ChanLoaderResponse;
import org.floens.chan.chan.ChanParser;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.DatabaseSavedReplyManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.utils.Time;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

/**
 * Process a typical imageboard json response.<br>
 * This class is highly multithreaded, take good care to not access models that are to be only
 * changed on the main thread.
 */
public class Chan4ReaderRequest extends JsonReaderRequest<ChanLoaderResponse> {
    private static final String TAG = "Chan4ReaderRequest";
    private static final boolean LOG_TIMING = false;

    private static final int THREAD_COUNT;
    private static final ExecutorService EXECUTOR;

    static {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    @Inject
    DatabaseManager databaseManager;

    @Inject
    FilterEngine filterEngine;

    @Inject
    ChanParser chanParser;

    private Loadable loadable;
    private List<Post> cached;
    private Post.Builder op;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private List<Filter> filters;
    private long startLoad;

    public Chan4ReaderRequest(ChanLoaderRequestParams request) {
        super(getChanUrl(request.loadable), request.listener, request.errorListener);
        getGraph().inject(this);

        // Copy the loadable and cached list. The cached array may changed/cleared by other threads.
        loadable = request.loadable.copy();
        cached = new ArrayList<>(request.cached);

        filters = new ArrayList<>();
        List<Filter> enabledFilters = filterEngine.getEnabledFilters();
        for (int i = 0; i < enabledFilters.size(); i++) {
            Filter filter = enabledFilters.get(i);

            if (filter.allBoards) {
                // copy the filter because it will get used on other threads
                filters.add(filter.copy());
            } else {
                String[] boardCodes = filter.boardCodes();
                for (String code : boardCodes) {
                    if (code.equals(loadable.boardCode)) {
                        // copy the filter because it will get used on other threads
                        filters.add(filter.copy());
                        break;
                    }
                }
            }
        }

        startLoad = Time.startTiming();

        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();
    }

    private static String getChanUrl(Loadable loadable) {
        String url;

        if (loadable.site == null) {
            throw new NullPointerException("Loadable.site == null");
        }

        if (loadable.board == null) {
            throw new NullPointerException("Loadable.board == null");
        }

        if (loadable.isThreadMode()) {
            url = loadable.site.endpoints().thread(loadable.board, loadable);
        } else if (loadable.isCatalogMode()) {
            url = loadable.site.endpoints().catalog(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }
        return url;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public ChanLoaderResponse readJson(JsonReader reader) throws Exception {
        if (LOG_TIMING) {
            Time.endTiming("Network", startLoad);
        }

        long load = Time.startTiming();

        ProcessingQueue processing = new ProcessingQueue();

        Map<Integer, Post> cachedByNo = new HashMap<>();
        for (int i = 0; i < cached.size(); i++) {
            Post cache = cached.get(i);
            cachedByNo.put(cache.no, cache);
        }

        if (loadable.isThreadMode()) {
            loadThread(reader, processing, cachedByNo);
        } else if (loadable.isCatalogMode()) {
            loadCatalog(reader, processing, cachedByNo);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        if (LOG_TIMING) {
            Time.endTiming("Load json", load);
        }

        List<Post> list = parsePosts(processing);
        return processPosts(list);
    }

    // Concurrently parses the new posts with an executor
    private List<Post> parsePosts(ProcessingQueue queue) throws InterruptedException, ExecutionException {
        long parsePosts = Time.startTiming();

        List<Post> total = new ArrayList<>();

        total.addAll(queue.cached);

        List<Callable<Post>> tasks = new ArrayList<>(queue.toParse.size());
        for (int i = 0; i < queue.toParse.size(); i++) {
            Post.Builder post = queue.toParse.get(i);
            tasks.add(new PostParseCallable(filterEngine, filters, databaseSavedReplyManager, post, chanParser));
        }

        if (!tasks.isEmpty()) {
            List<Future<Post>> futures = EXECUTOR.invokeAll(tasks);
            for (int i = 0; i < futures.size(); i++) {
                Future<Post> future = futures.get(i);
                Post parsedPost = future.get();
                if (parsedPost != null) {
                    total.add(parsedPost);
                }
            }

            if (LOG_TIMING) {
                Time.endTiming("Parse posts with " + THREAD_COUNT + " threads", parsePosts);
            }
        }

        return total;
    }

    private ChanLoaderResponse processPosts(List<Post> serverPosts) throws Exception {
        ChanLoaderResponse response = new ChanLoaderResponse(op, new ArrayList<Post>(serverPosts.size()));

        List<Post> cachedPosts = new ArrayList<>();
        List<Post> newPosts = new ArrayList<>();
        if (cached.size() > 0) {
            long deleteCheck = Time.startTiming();
            // Add all posts that were parsed before
            cachedPosts.addAll(cached);

            Map<Integer, Post> cachedPostsByNo = new HashMap<>();
            for (int i = 0; i < cachedPosts.size(); i++) {
                Post post = cachedPosts.get(i);
                cachedPostsByNo.put(post.no, post);
            }

            Map<Integer, Post> serverPostsByNo = new HashMap<>();
            for (int i = 0; i < serverPosts.size(); i++) {
                Post post = serverPosts.get(i);
                serverPostsByNo.put(post.no, post);
            }

            // If there's a cached post but it's not in the list received from the server, mark it as deleted
            if (loadable.isThreadMode()) {
                for (int i = 0; i < cachedPosts.size(); i++) {
                    Post cachedPost = cachedPosts.get(i);
                    cachedPost.deleted.set(!serverPostsByNo.containsKey(cachedPost.no));
                }
            }
            if (LOG_TIMING) {
                Time.endTiming("Delete check", deleteCheck);
            }
            long newCheck = Time.startTiming();

            // If there's a post in the list from the server, that's not in the cached list, add it.
            for (int i = 0; i < serverPosts.size(); i++) {
                Post serverPost = serverPosts.get(i);
                if (!cachedPostsByNo.containsKey(serverPost.no)) {
                    newPosts.add(serverPost);
                }
            }
            if (LOG_TIMING) {
                Time.endTiming("New check", newCheck);
            }
        } else {
            newPosts.addAll(serverPosts);
        }

        List<Post> allPosts = new ArrayList<>(cachedPosts.size() + newPosts.size());
        allPosts.addAll(cachedPosts);
        allPosts.addAll(newPosts);

        if (loadable.isThreadMode()) {
            Map<Integer, Post> postsByNo = new HashMap<>();
            for (int i = 0; i < allPosts.size(); i++) {
                Post post = allPosts.get(i);
                postsByNo.put(post.no, post);
            }

            // Maps post no's to a list of no's that that post received replies from
            Map<Integer, List<Integer>> replies = new HashMap<>();

            long collectReplies = Time.startTiming();
            for (int i = 0; i < allPosts.size(); i++) {
                Post sourcePost = allPosts.get(i);

                for (int replyTo : sourcePost.repliesTo) {
                    List<Integer> value = replies.get(replyTo);
                    if (value == null) {
                        value = new ArrayList<>(3);
                        replies.put(replyTo, value);
                    }
                    value.add(sourcePost.no);
                }
            }
            if (LOG_TIMING) {
                Time.endTiming("Collect replies", collectReplies);
            }
            long mapReplies = Time.startTiming();

            for (Map.Entry<Integer, List<Integer>> entry : replies.entrySet()) {
                int key = entry.getKey();
                List<Integer> value = entry.getValue();

                Post subject = postsByNo.get(key);
                synchronized (subject.repliesFrom) {
                    subject.repliesFrom.clear();
                    subject.repliesFrom.addAll(value);
                }
            }

            if (LOG_TIMING) {
                Time.endTiming("Map replies", mapReplies);
            }
        }

        response.posts.addAll(allPosts);

        return response;
    }

    private void loadThread(JsonReader reader, ProcessingQueue queue, Map<Integer, Post> cachedByNo) throws Exception {
        reader.beginObject();
        // Page object
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("posts")) {
                reader.beginArray();
                // Thread array
                while (reader.hasNext()) {
                    // Thread object
                    readPostObject(reader, queue, cachedByNo);
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
    }

    private void loadCatalog(JsonReader reader, ProcessingQueue queue, Map<Integer, Post> cachedByNo) throws Exception {
        reader.beginArray(); // Array of pages

        while (reader.hasNext()) {
            reader.beginObject(); // Page object

            while (reader.hasNext()) {
                if (reader.nextName().equals("threads")) {
                    reader.beginArray(); // Threads array

                    while (reader.hasNext()) {
                        readPostObject(reader, queue, cachedByNo);
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

    private void readPostObject(JsonReader reader, ProcessingQueue queue, Map<Integer, Post> cachedByNo) throws Exception {
        Post.Builder builder = new Post.Builder();
        builder.board(loadable.board);

        // File
        long fileId = 0;
        String fileExt = null;
        int fileWidth = 0;
        int fileHeight = 0;
        long fileSize = 0;
        boolean fileSpoiler = false;
        String fileName = null;

        // Country flag
        String countryCode = null;
        String countryName = null;

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
                    fileId = reader.nextLong();
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
                default:
                    // Unknown/ignored key
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (builder.op) {
            // Update OP fields later on the main thread
            op = new Post.Builder();
            op.closed(builder.closed);
            op.archived(builder.archived);
            op.sticky(builder.sticky);
            op.replies(builder.replies);
            op.images(builder.images);
            op.uniqueIps(builder.uniqueIps);
        }

        Post cached = cachedByNo.get(builder.id);
        if (cached != null) {
            // Id is known, use the cached post object.
            queue.cached.add(cached);
            return;
        }

        SiteEndpoints endpoints = loadable.getSite().endpoints();
        if (fileId != 0 && fileName != null && fileExt != null) {
            Map<String, String> hack = new HashMap<>(2);
            hack.put("tim", String.valueOf(fileId));
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
            String countryUrl = endpoints.flag(builder, countryCode, Collections.<String, String>emptyMap());
            builder.country(countryCode, countryName, countryUrl);
        }

        queue.toParse.add(builder);
    }

    private static class ProcessingQueue {
        public List<Post> cached = new ArrayList<>();
        public List<Post.Builder> toParse = new ArrayList<>();
    }
}
