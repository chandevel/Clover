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
package org.floens.chan.core.net;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.DatabaseSavedReplyManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ChanReaderRequest extends JsonReaderRequest<ChanReaderRequest.ChanReaderResponse> {
    private static final String TAG = "ChanReaderRequest";
    private static final boolean LOG_TIMING = false;

    private static final int THREAD_COUNT;
    private static ExecutorService EXECUTOR;

    static {
        THREAD_COUNT = Runtime.getRuntime().availableProcessors();
        EXECUTOR = Executors.newFixedThreadPool(THREAD_COUNT);
    }

    private Loadable loadable;
    private List<Post> cached;
    private Post op;
    private FilterEngine filterEngine;
    private DatabaseManager databaseManager;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private List<Filter> filters;
    private long startLoad;

    private ChanReaderRequest(String url, Listener<ChanReaderResponse> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
        filterEngine = FilterEngine.getInstance();
        databaseManager = Chan.getDatabaseManager();
        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();
    }

    public static ChanReaderRequest newInstance(Loadable loadable, List<Post> cached, Listener<ChanReaderResponse> listener, ErrorListener errorListener) {
        String url;

        if (loadable.isThreadMode()) {
            url = ChanUrls.getThreadUrl(loadable.board, loadable.no);
        } else if (loadable.isCatalogMode()) {
            url = ChanUrls.getCatalogUrl(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        ChanReaderRequest request = new ChanReaderRequest(url, listener, errorListener);

        // Copy the loadable and cached list. The cached array may changed/cleared by other threads.
        request.loadable = loadable.copy();
        request.cached = new ArrayList<>(cached);

        request.filters = new ArrayList<>();
        List<Filter> enabledFilters = request.filterEngine.getEnabledFilters();
        for (int i = 0; i < enabledFilters.size(); i++) {
            Filter filter = enabledFilters.get(i);

            if (filter.allBoards) {
                // copy the filter because it will get used on other threads
                request.filters.add(filter.copy());
            } else {
                String[] boardCodes = filter.boardCodes();
                for (String code : boardCodes) {
                    if (code.equals(loadable.board)) {
                        // copy the filter because it will get used on other threads
                        request.filters.add(filter.copy());
                        break;
                    }
                }
            }
        }

        request.startLoad = Time.startTiming();

        return request;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public ChanReaderResponse readJson(JsonReader reader) throws Exception {
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
            Post post = queue.toParse.get(i);
            tasks.add(new PostParseCallable(filterEngine, filters, databaseSavedReplyManager, post));
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

    private ChanReaderResponse processPosts(List<Post> serverPosts) throws Exception {
        ChanReaderResponse response = new ChanReaderResponse();
        response.posts = new ArrayList<>(serverPosts.size());
        response.op = op;

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
        Post post = new Post();
        post.board = loadable.board;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "no":
                    post.no = reader.nextInt();
                    break;
                case "now":
                    post.date = reader.nextString();
                    break;
                case "name":
                    post.name = reader.nextString();
                    break;
                case "com":
                    post.rawComment = reader.nextString();
                    break;
                case "tim":
                    post.tim = reader.nextLong();
                    break;
                case "time":
                    post.time = reader.nextLong();
                    break;
                case "ext":
                    post.ext = reader.nextString().replace(".", "");
                    break;
                case "resto":
                    post.resto = reader.nextInt();
                    break;
                case "w":
                    post.imageWidth = reader.nextInt();
                    break;
                case "h":
                    post.imageHeight = reader.nextInt();
                    break;
                case "fsize":
                    post.fileSize = reader.nextLong();
                    break;
                case "sub":
                    post.subject = reader.nextString();
                    break;
                case "replies":
                    post.replies = reader.nextInt();
                    break;
                case "filename":
                    post.filename = reader.nextString();
                    break;
                case "sticky":
                    post.sticky = reader.nextInt() == 1;
                    break;
                case "closed":
                    post.closed = reader.nextInt() == 1;
                    break;
                case "archived":
                    post.archived = reader.nextInt() == 1;
                    break;
                case "trip":
                    post.tripcode = reader.nextString();
                    break;
                case "country":
                    post.country = reader.nextString();
                    break;
                case "country_name":
                    post.countryName = reader.nextString();
                    break;
                case "troll_country":
                    post.trollCountry = reader.nextString();
                    break;
                case "id":
                    post.id = reader.nextString();
                    break;
                case "capcode":
                    post.capcode = reader.nextString();
                    break;
                case "images":
                    post.images = reader.nextInt();
                    break;
                case "spoiler":
                    post.spoiler = reader.nextInt() == 1;
                    break;
                case "unique_ips":
                    post.uniqueIps = reader.nextInt();
                    break;
                default:
                    // Unknown/ignored key
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (post.resto == 0) {
            // Update OP fields later on the main thread
            op = new Post();
            op.closed = post.closed;
            op.archived = post.archived;
            op.sticky = post.sticky;
            op.replies = post.replies;
            op.images = post.images;
            op.uniqueIps = post.uniqueIps;
        }

        Post cached = cachedByNo.get(post.no);
        if (cached != null) {
            queue.cached.add(cached);
        } else {
            queue.toParse.add(post);
        }
    }

    public static class ChanReaderResponse {
        // Op Post that is created new each time.
        // Used to later copy members like image count to the real op on the main thread.
        public Post op;
        public List<Post> posts;
    }

    private static class ProcessingQueue {
        public List<Post> cached = new ArrayList<>();
        public List<Post> toParse = new ArrayList<>();
    }
}
