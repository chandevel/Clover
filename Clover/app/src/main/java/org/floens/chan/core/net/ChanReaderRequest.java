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
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class ChanReaderRequest extends JsonReaderRequest<ChanReaderRequest.ChanReaderResponse> {
    private static final String TAG = "ChanReaderRequest";

    private Loadable loadable;
    private List<Post> cached;
    private Post op;
    private FilterEngine filterEngine;

    private ChanReaderRequest(String url, Listener<ChanReaderResponse> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
        filterEngine = FilterEngine.getInstance();
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

        return request;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public ChanReaderResponse readJson(JsonReader reader) throws Exception {
        List<Post> list;

        if (loadable.isThreadMode()) {
            list = loadThread(reader);
        } else if (loadable.isCatalogMode()) {
            list = loadCatalog(reader);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        return processPosts(list);
    }

    private ChanReaderResponse processPosts(List<Post> serverList) throws Exception {
        ChanReaderResponse response = new ChanReaderResponse();
        response.posts = new ArrayList<>(serverList.size());
        response.op = op;

        if (cached.size() > 0) {
            // Add all posts that were parsed before
            response.posts.addAll(cached);

            // If there's a cached post but it's not in the list received from the server, mark it as deleted
            if (loadable.isThreadMode()) {
                boolean serverHas;
                for (Post cache : cached) {
                    serverHas = false;
                    for (Post b : serverList) {
                        if (b.no == cache.no) {
                            serverHas = true;
                            break;
                        }
                    }

                    cache.deleted.set(!serverHas);
                }
            }

            // If there's a post in the list from the server, that's not in the cached list, add it.
            boolean known;
            for (Post post : serverList) {
                known = false;

                for (Post cache : cached) {
                    if (cache.no == post.no) {
                        known = true;
                        break;
                    }
                }

                if (!known) {
                    response.posts.add(post);
                }
            }
        } else {
            response.posts.addAll(serverList);
        }

        for (Post post : response.posts) {
            post.isSavedReply.set(Chan.getDatabaseManager().isSavedReply(post.board, post.no));
        }

        return response;
    }

    private List<Post> loadThread(JsonReader reader) throws Exception {
        ArrayList<Post> list = new ArrayList<>();

        reader.beginObject();
        // Page object
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("posts")) {
                reader.beginArray();
                // Thread array
                while (reader.hasNext()) {
                    // Thread object
                    Post post = readPostObject(reader);
                    if (post != null) {
                        list.add(post);
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return list;
    }

    private List<Post> loadCatalog(JsonReader reader) throws Exception {
        ArrayList<Post> list = new ArrayList<>();

        reader.beginArray(); // Array of pages

        while (reader.hasNext()) {
            reader.beginObject(); // Page object

            while (reader.hasNext()) {
                if (reader.nextName().equals("threads")) {
                    reader.beginArray(); // Threads array

                    while (reader.hasNext()) {
                        Post post = readPostObject(reader);
                        if (post != null) {
                            list.add(post);
                        }
                    }

                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }

            reader.endObject();
        }

        reader.endArray();

        return list;
    }

    private Post readPostObject(JsonReader reader) throws Exception {
        Post post = new Post();
        post.board = loadable.board;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "no":
                    // Post number
                    post.no = reader.nextInt();
                /*} else if (key.equals("time")) {
                    // Time
                    long time = reader.nextLong();
                    post.date = new Date(time * 1000);*/
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
                    post.fileSize = reader.nextInt();
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

        Post cached = null;
        for (Post item : this.cached) {
            if (item.no == post.no) {
                cached = item;

                break;
            }
        }

        if (cached != null) {
            return cached;
        } else {
            if (!post.finish()) {
                Logger.e(TAG, "Incorrect data about post received for post " + post.no);
                return null;
            } else {
                processPostFilter(post);
                return post;
            }
        }
    }

    private void processPostFilter(Post post) {
        synchronized (filterEngine.getEnabledFiltersLock()) {
            List<Filter> filters = filterEngine.getEnabledFilters();
            int filterSize = filters.size();
            for (int i = 0; i < filterSize; i++) {
                Filter filter = filters.get(i);
                if (filterEngine.matches(filter, post)) {
                    FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
                    switch (action) {
                        case COLOR:
                            post.filterHighlightedColor = filter.color;
                            break;
                        case HIDE:
                            post.filterStub = true;
                            break;
                    }
                }
            }
        }
    }

    public static class ChanReaderResponse {
        // Op Post that is created new each time.
        // Used to later copy members like image count to the real op on the main thread.
        public Post op;
        public List<Post> posts;
    }
}
