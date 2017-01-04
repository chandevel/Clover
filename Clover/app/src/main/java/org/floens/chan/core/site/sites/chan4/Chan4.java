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

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.floens.chan.chan.ChanLoaderRequest;
import org.floens.chan.chan.ChanLoaderRequestParams;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.HttpCallManager;
import org.floens.chan.core.site.http.LoginRequest;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

public class Chan4 implements Site {
    private static final String TAG = "Chan4";

    private static final Random random = new Random();

    @Inject
    HttpCallManager httpCallManager;

    @Inject
    RequestQueue requestQueue;

    private final SiteEndpoints endpoints = new SiteEndpoints() {
        @Override
        public String catalog(Board board) {
            return "https://a.4cdn.org/" + board.code + "/catalog.json";
        }

        @Override
        public String thread(Board board, Loadable loadable) {
            return "https://a.4cdn.org/" + board.code + "/thread/" + loadable.no + ".json";
        }

        @Override
        public String imageUrl(Post.Builder post, Map<String, String> arg) {
            return "https://i.4cdn.org/" + post.board.code + "/" + arg.get("tim") + "." + arg.get("ext");
        }

        @Override
        public String thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            if (spoiler) {
                if (post.board.customSpoilers >= 0) {
                    int i = random.nextInt(post.board.customSpoilers) + 1;
                    return "https://s.4cdn.org/image/spoiler-" + post.board.code + i + ".png";
                } else {
                    return "https://s.4cdn.org/image/spoiler.png";
                }
            } else {
                return "https://t.4cdn.org/" + post.board.code + "/" + arg.get("tim") + "s.jpg";
            }
        }

        @Override
        public String flag(Post.Builder post, String countryCode, Map<String, String> arg) {
            return "https://s.4cdn.org/image/country/" + countryCode.toLowerCase(Locale.ENGLISH) + ".gif";
        }

        @Override
        public String boards() {
            return "https://a.4cdn.org/boards.json";
        }

        @Override
        public String reply(Loadable loadable) {
            return "https://sys.4chan.org/" + loadable.getBoard().code + "/post";
        }

        @Override
        public String delete(Post post) {
            return "https://sys.4chan.org/" + post.board.code + "/imgboard.php";
        }

        @Override
        public String report(Post post) {
            return "https://sys.4chan.org/" + post.board.code + "/imgboard.php?mode=report&no=" + post.no;
        }

        @Override
        public String login() {
            return "https://sys.4chan.org/auth";
        }
    };

    public Chan4() {
        getGraph().inject(this);
    }

    /**
     * <b>Note: very special case, only this site may have 0 as the return value.<br>
     * This is for backwards compatibility when we didn't support multi-site yet.</b>
     *
     * @return {@inheritDoc}
     */
    @Override
    public int id() {
        return 0;
    }

    @Override
    public boolean feature(Feature feature) {
        switch (feature) {
            case POSTING:
                // yes, we support posting.
                return true;
            case LOGIN:
                // 4chan pass.
                return true;
            case POST_DELETE:
                // yes, with the password saved when posting.
                return true;
            case POST_REPORT:
                // yes, with a custom url
                return true;
            default:
                return false;
        }
    }

    @Override
    public BoardsType boardsType() {
        // yes, boards.json
        return BoardsType.DYNAMIC;
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        switch (boardFeature) {
            case POSTING_IMAGE:
                // yes, we support image posting.
                return true;
            case POSTING_SPOILER:
                // depends if the board supports it.
                return board.spoilers;
            default:
                return false;
        }
    }

    @Override
    public Board board(String name) {
        List<Board> allBoards = getGraph().getBoardManager().getAllBoards();
        for (Board board : allBoards) {
            if (board.code.equals(name)) {
                return board;
            }
        }

        return null;
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public void boards(final BoardsListener listener) {
        requestQueue.add(new Chan4BoardsRequest(this, new Response.Listener<List<Board>>() {
            @Override
            public void onResponse(List<Board> response) {
                listener.onBoardsReceived(new Boards(response));
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Logger.e(TAG, "Failed to get boards from server", error);

                // API fail, provide some default boards
                List<Board> list = new ArrayList<>();
                list.add(new Board(Chan4.this, "Technology", "g", true, true));
                list.add(new Board(Chan4.this, "Food & Cooking", "ck", true, true));
                list.add(new Board(Chan4.this, "Do It Yourself", "diy", true, true));
                list.add(new Board(Chan4.this, "Animals & Nature", "an", true, true));
                Collections.shuffle(list);
                listener.onBoardsReceived(new Boards(list));
            }
        }));
    }

    @Override
    public ChanLoaderRequest loaderRequest(ChanLoaderRequestParams request) {
        return new ChanLoaderRequest(new Chan4ReaderRequest(request));
    }

    @Override
    public void post(Reply reply, final PostListener postListener) {
        httpCallManager.makeHttpCall(new Chan4ReplyHttpCall(reply), new HttpCall.HttpCallback<Chan4ReplyHttpCall>() {
            @Override
            public void onHttpSuccess(Chan4ReplyHttpCall httpPost) {
                postListener.onPostComplete(httpPost, httpPost.replyResponse);
            }

            @Override
            public void onHttpFail(Chan4ReplyHttpCall httpPost, Exception e) {
                postListener.onPostError(httpPost);
            }
        });
    }

    @Override
    public void delete(DeleteRequest deleteRequest, final DeleteListener deleteListener) {
        httpCallManager.makeHttpCall(new Chan4DeleteHttpCall(deleteRequest), new HttpCall.HttpCallback<Chan4DeleteHttpCall>() {
            @Override
            public void onHttpSuccess(Chan4DeleteHttpCall httpPost) {
                deleteListener.onDeleteComplete(httpPost, httpPost.deleteResponse);
            }

            @Override
            public void onHttpFail(Chan4DeleteHttpCall httpPost, Exception e) {
                deleteListener.onDeleteError(httpPost);
            }
        });
    }

    @Override
    public void login(LoginRequest loginRequest, final LoginListener loginListener) {
        httpCallManager.makeHttpCall(new Chan4PassHttpCall(loginRequest), new HttpCall.HttpCallback<Chan4PassHttpCall>() {
            @Override
            public void onHttpSuccess(Chan4PassHttpCall httpCall) {
                loginListener.onLoginComplete(httpCall, httpCall.loginResponse);
            }

            @Override
            public void onHttpFail(Chan4PassHttpCall httpCall, Exception e) {
                loginListener.onLoginError(httpCall);
            }
        });
    }
}
