package org.floens.chan.core.site;

import org.floens.chan.chan.ChanLoaderRequest;
import org.floens.chan.chan.ChanLoaderRequestParams;
import org.floens.chan.core.model.Board;

public interface Site {
    enum Feature {
        /**
         * This site supports posting. (Or rather, we've implemented support for it.)
         */
        POSTING,

        /**
         * This site supports deleting posts.
         */
        POST_DELETE,

        /**
         * This site supports some sort of login (like 4pass).
         */
        LOGIN
    }

    /**
     * Features available to check when {@link Feature#POSTING} is {@code true}.
     */
    enum BoardFeature {
        /**
         * This board supports posting with images.
         */
        POSTING_IMAGE,

        /**
         * This board supports posting with a checkbox to mark the posted image as a spoiler.
         */
        POSTING_SPOILER,
    }

    /**
     * How the boards are organized for this size.
     */
    enum BoardsType {
        /**
         * The site's boards are static, hard-coded in the site.
         */
        STATIC,

        /**
         * The site's boards are dynamic, a boards.json like endpoint is available to get the available boards.
         */
        DYNAMIC,

        /**
         * The site's boards are dynamic and infinite, existence of boards should be checked per board.
         */
        INFINITE
    }

    /**
     * Global positive (>0) integer that uniquely identifies this site.<br>
     * This id will be persisted in the database.
     *
     * @return a positive (>0) integer that uniquely identifies this site.
     */
    int id();

    boolean feature(Feature feature);

    boolean boardFeature(BoardFeature boardFeature, Board board);

    SiteEndpoints endpoints();

    BoardsType boardsType();

    void boards(BoardsListener boardsListener);

    Board board(String name);

    ChanLoaderRequest loaderRequest(ChanLoaderRequestParams request);

    interface BoardsListener {
        void onBoardsReceived(Boards boards);
    }
}
