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
package org.floens.chan.core.site;

import org.floens.chan.chan.ChanLoaderRequest;
import org.floens.chan.chan.ChanLoaderRequestParams;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.DeleteResponse;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.LoginRequest;
import org.floens.chan.core.site.http.LoginResponse;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;

public interface Site {
    enum Feature {
        /**
         * This site supports posting. (Or rather, we've implemented support for it.)
         *
         * @see #post(Reply, PostListener)
         * @see SiteEndpoints#reply(Loadable)
         */
        POSTING,

        /**
         * This site supports deleting posts.
         *
         * @see #delete(DeleteRequest, DeleteListener)
         * @see SiteEndpoints#delete(Post)
         */
        POST_DELETE,

        /**
         * This site supports reporting posts.
         *
         * @see SiteEndpoints#report(Post)
         */
        POST_REPORT,

        /**
         * This site supports some sort of login (like 4pass).
         *
         * @see #login(LoginRequest, LoginListener)
         * @see SiteEndpoints#login()
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

    interface BoardsListener {
        void onBoardsReceived(Boards boards);
    }

    Board board(String name);

    ChanLoaderRequest loaderRequest(ChanLoaderRequestParams request);

    void post(Reply reply, PostListener postListener);

    interface PostListener {
        void onPostComplete(HttpCall httpCall, ReplyResponse replyResponse);

        void onPostError(HttpCall httpCall);
    }

    void delete(DeleteRequest deleteRequest, DeleteListener deleteListener);

    interface DeleteListener {
        void onDeleteComplete(HttpCall httpCall, DeleteResponse deleteResponse);

        void onDeleteError(HttpCall httpCall);
    }

    void login(LoginRequest loginRequest, LoginListener loginListener);

    interface LoginListener {
        void onLoginComplete(HttpCall httpCall, LoginResponse loginResponse);

        void onLoginError(HttpCall httpCall);
    }
}
