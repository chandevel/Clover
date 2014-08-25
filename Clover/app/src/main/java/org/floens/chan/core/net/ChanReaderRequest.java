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

import org.floens.chan.ChanApplication;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChanReaderRequest extends JsonReaderRequest<List<Post>> {
    private Loadable loadable;
    private List<Post> cached;

    private ChanReaderRequest(String url, Listener<List<Post>> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
    }

    public static ChanReaderRequest newInstance(Loadable loadable, List<Post> cached, Listener<List<Post>> listener,
                                                ErrorListener errorListener) {
        String url;

        if (loadable.isBoardMode()) {
            url = ChanUrls.getPageUrl(loadable.board, loadable.no);
        } else if (loadable.isThreadMode()) {
            url = ChanUrls.getThreadUrl(loadable.board, loadable.no);
        } else if (loadable.isCatalogMode()) {
            url = ChanUrls.getCatalogUrl(loadable.board);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        ChanReaderRequest request = new ChanReaderRequest(url, listener, errorListener);
        request.loadable = loadable;
        request.cached = cached;

        return request;
    }

    @Override
    public Priority getPriority() {
        return Priority.HIGH;
    }

    @Override
    public List<Post> readJson(JsonReader reader) throws Exception {
        List<Post> list;

        if (loadable.isBoardMode()) {
            list = loadBoard(reader);
        } else if (loadable.isThreadMode()) {
            list = loadThread(reader);
        } else if (loadable.isCatalogMode()) {
            list = loadCatalog(reader);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        return processPosts(list);
    }

    private List<Post> processPosts(List<Post> serverList) throws Exception {
        List<Post> totalList = new ArrayList<>(serverList.size());

        if (cached.size() > 0) {
            totalList.addAll(cached);

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

                    cache.deleted = !serverHas;
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

                // serverPost is not in finalList
                if (!known) {
                    totalList.add(post);
                }
            }

            // Sort if it got out of order due to posts disappearing/reappearing
            /*if (loadable.isThreadMode()) {
                Collections.sort(totalList, new Comparator<Post>() {
                    @Override
                    public int compare(Post lhs, Post rhs) {
                        return lhs.time == rhs.time ? 0 : (lhs.time < rhs.time ? -1 : 1);
                    }
                });
            }*/

        } else {
            totalList.addAll(serverList);
        }

        Set<Integer> invalidatedPosts = new HashSet<>();
        for (Post post : totalList) {
            if (!post.deleted) {
                post.repliesFrom.clear();

                for (Post other : totalList) {
                    if (other.repliesTo.contains(post.no) && !other.deleted) {
                        post.repliesFrom.add(other.no);
                    }
                }
            } else {
                post.repliesTo.clear();

                for (int no : post.repliesFrom) {
                    invalidatedPosts.add(no);
                }

                post.repliesFrom.clear();
            }
        }

        for (int no : invalidatedPosts) {
            for (Post post : totalList) {
                if (post.no == no) {
                    if (!post.finish()) {
                        throw new IOException("Incorrect data about post received.");
                    }
                    break;
                }
            }
        }

        for (Post post : totalList) {
            post.isSavedReply = ChanApplication.getDatabaseManager().isSavedReply(post.board, post.no);
        }

        return totalList;
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
                    list.add(readPostObject(reader));
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return list;
    }

    private List<Post> loadBoard(JsonReader reader) throws Exception {
        ArrayList<Post> list = new ArrayList<>();

        reader.beginObject(); // Threads array

        if (reader.nextName().equals("threads")) {
            reader.beginArray();

            while (reader.hasNext()) {
                reader.beginObject(); // Thread object

                if (reader.nextName().equals("posts")) {
                    reader.beginArray();

                    list.add(readPostObject(reader));

                    // Only consume one post
                    while (reader.hasNext())
                        reader.skipValue();

                    reader.endArray();
                } else {
                    reader.skipValue();
                }

                reader.endObject();
            }

            reader.endArray();
        } else {
            reader.skipValue();
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
                        list.add(readPostObject(reader));
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
                default:
                    // Unknown/ignored key
                    //                log("Unknown/ignored key: " + key + ".");
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        Post cachedResult = null;
        for (Post possibleCached : cached) {
            if (possibleCached.no == post.no) {
                cachedResult = possibleCached;
                break;
            }
        }

        if (cachedResult != null) {
            return cachedResult;
        } else {
            if (!post.finish()) {
                throw new IOException("Incorrect data about post received.");
            }

            return post;
        }
    }
}
