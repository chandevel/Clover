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

import com.android.volley.ParseError;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.ChanApplication;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChanReaderRequest extends JsonReaderRequest<List<Post>> {
    private Loadable loadable;
    private List<Post> cached;

    private ChanReaderRequest(String url, Listener<List<Post>> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
    }

    /**
     * Creates a ChanReaderRequest with supplied params
     *
     * @param mode          ThreadManager mode
     * @param board         board key
     * @param no            page for board, no for threads
     * @param listener
     * @param errorListener
     * @return New instance of ChanReaderRequest
     */
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
    public List<Post> readJson(JsonReader reader) {
        List<Post> list = new ArrayList<Post>();

        if (loadable.isBoardMode()) {
            list = loadBoard(reader);
        } else if (loadable.isThreadMode()) {
            list = loadThread(reader);
        } else if (loadable.isCatalogMode()) {
            list = loadCatalog(reader);
        } else {
            throw new IllegalArgumentException("Unknown mode");
        }

        processPosts(list);

        return list;
    }

    private void processPosts(List<Post> posts) {
        for (Post post : posts) {
            post.repliesFrom.clear();

            for (Post other : posts) {
                if (other.repliesTo.contains(post.no)) {
                    post.repliesFrom.add(other.no);
                }
            }

            post.isSavedReply = ChanApplication.getDatabaseManager().isSavedReply(post.board, post.no);
        }
    }

    private List<Post> loadThread(JsonReader reader) {
        ArrayList<Post> list = new ArrayList<Post>();

        try {
            reader.beginObject();
            // Page object
            while (reader.hasNext()) {
                if (reader.nextName().equals("posts")) {
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
        } catch (IOException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (IllegalStateException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        }

        return list;
    }

    private List<Post> loadBoard(JsonReader reader) {
        ArrayList<Post> list = new ArrayList<Post>();

        try {
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
        } catch (IOException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (IllegalStateException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        }

        return list;
    }

    private List<Post> loadCatalog(JsonReader reader) {
        ArrayList<Post> list = new ArrayList<Post>();

        try {
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
        } catch (IOException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        } catch (IllegalStateException e) {
            e.printStackTrace();
            setError(new ParseError(e));
        }

        return list;
    }

    private Post readPostObject(JsonReader reader) throws IllegalStateException, NumberFormatException, IOException {
        Post post = new Post();
        post.board = loadable.board;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();

            if (key.equals("no")) {
                // Post number
                post.no = reader.nextInt();
                /*} else if (key.equals("time")) {
                    // Time
                    long time = reader.nextLong();
                    post.date = new Date(time * 1000);*/
            } else if (key.equals("now")) {
                post.date = reader.nextString();
            } else if (key.equals("name")) {
                post.name = reader.nextString();
            } else if (key.equals("com")) {
                post.setComment(reader.nextString());
            } else if (key.equals("tim")) {
                post.tim = reader.nextString();
            } else if (key.equals("time")) {
                post.time = reader.nextLong();
            } else if (key.equals("email")) {
                post.email = reader.nextString();
            } else if (key.equals("ext")) {
                post.ext = reader.nextString().replace(".", "");
            } else if (key.equals("resto")) {
                post.resto = reader.nextInt();
            } else if (key.equals("w")) {
                post.imageWidth = reader.nextInt();
            } else if (key.equals("h")) {
                post.imageHeight = reader.nextInt();
            } else if (key.equals("fsize")) {
                post.fileSize = reader.nextInt();
            } else if (key.equals("sub")) {
                post.subject = reader.nextString();
            } else if (key.equals("replies")) {
                post.replies = reader.nextInt();
            } else if (key.equals("filename")) {
                post.filename = reader.nextString();
            } else if (key.equals("sticky")) {
                post.sticky = reader.nextInt() == 1;
            } else if (key.equals("closed")) {
                post.closed = reader.nextInt() == 1;
            } else if (key.equals("trip")) {
                post.tripcode = reader.nextString();
            } else if (key.equals("country")) {
                post.country = reader.nextString();
            } else if (key.equals("country_name")) {
                post.countryName = reader.nextString();
            } else if (key.equals("id")) {
                post.id = reader.nextString();
            } else if (key.equals("capcode")) {
                post.capcode = reader.nextString();
            } else {
                // Unknown/ignored key
                //                log("Unknown/ignored key: " + key + ".");
                reader.skipValue();
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
            if (!post.finish(loadable)) {
                throw new IOException("Incorrect data about post received.");
            }

            return post;
        }
    }
}
