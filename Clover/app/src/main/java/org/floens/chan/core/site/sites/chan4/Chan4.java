package org.floens.chan.core.site.sites.chan4;

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteEndpoints;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.floens.chan.Chan.getGraph;

public class Chan4 implements Site {
    private static final String TAG = "Chan4";

    private static final Random random = new Random();

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
        public String imageUrl(Post post) {
            return "https://i.4cdn.org/" + post.boardId + "/" + Long.toString(post.tim) + "." + post.ext;
        }

        @Override
        public String thumbnailUrl(Post post) {
            if (post.spoiler) {
                if (post.board.customSpoilers >= 0) {
                    int i = random.nextInt(post.board.customSpoilers) + 1;
                    return "https://s.4cdn.org/image/spoiler-" + post.board.code + i + ".png";
                } else {
                    return "https://s.4cdn.org/image/spoiler.png";
                }
            } else {
                return "https://t.4cdn.org/" + post.board.code + "/" + post.tim + "s.jpg";
            }
        }

        @Override
        public String flag(Post post) {
            return "https://s.4cdn.org/image/country/" + post.country.toLowerCase(Locale.ENGLISH) + ".gif";
        }

        @Override
        public String boards() {
            return "https://a.4cdn.org/boards.json";
        }

        @Override
        public String reply(Board board, Loadable thread) {
            return "https://sys.4chan.org/" + board.code + "/post";
        }
    };

    public Chan4() {
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
            case DYNAMIC_BOARDS:
                // yes, boards.json
                return true;
            case LOGIN:
                // 4chan pass.
                return true;
            case POST_DELETE:
                // yes, with the password saved when posting.
                return true;
            default:
                return false;
        }
    }

    @Override
    public BoardsType boardsType() {
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
        getGraph().getRequestQueue().add(new Chan4BoardsRequest(this, new Response.Listener<List<Board>>() {
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
}
