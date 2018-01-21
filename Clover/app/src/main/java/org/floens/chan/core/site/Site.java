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

import android.support.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.settings.Setting;
import org.floens.chan.core.settings.json.JsonSettings;
import org.floens.chan.core.site.common.ChanReader;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.DeleteResponse;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.http.LoginRequest;
import org.floens.chan.core.site.http.LoginResponse;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;

import java.util.List;

import okhttp3.HttpUrl;

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
         * This site supports some sort of authentication (like 4pass).
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
        // TODO(multisite) use this
        POSTING_IMAGE,

        /**
         * This board supports posting with a checkbox to mark the posted image as a spoiler.
         */
        // TODO(multisite) use this
        POSTING_SPOILER,
    }

    /**
     * How the boards are organized for this size.
     */
    enum BoardsType {
        /**
         * The site's boards are static, there is no extra info for a board in the api.
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
     * Initialize the site with the given id, config, and userSettings.
     * <p><b>Note: do not use any managers at this point, because they rely on the sites being initialized.
     * Instead, use {@link #postInitialize()}</b>
     *
     * @param id           the site id
     * @param config       the site config
     * @param userSettings the site user settings
     */
    void initialize(int id, SiteConfig config, JsonSettings userSettings);

    void postInitialize();

    /**
     * Global positive (>0) integer that uniquely identifies this site.<br>
     * Use the id received from {@link #initialize(int, SiteConfig, JsonSettings)}.
     *
     * @return a positive (>0) integer that uniquely identifies this site.
     */
    int id();

    String name();

    SiteIcon icon();

    boolean respondsTo(HttpUrl url);

    Loadable resolve(HttpUrl url);

    boolean feature(Feature feature);

    boolean boardFeature(BoardFeature boardFeature, Board board);

    List<Setting<?>> settings();

    SiteEndpoints endpoints();

    SiteRequestModifier requestModifier();

    BoardsType boardsType();

    String desktopUrl(Loadable loadable, @Nullable Post post);

    void boards(BoardsListener boardsListener);

    interface BoardsListener {
        void onBoardsReceived(Boards boards);
    }

    /**
     * Return the board for this site with the given {@code code}.
     * <p>This does not need to create the board if it doesn't exist. This is important for
     * sites that have a board type different to DYNAMIC. Returning from the database is
     * enough.</p>
     *
     * @param code the board code
     * @return a board with the board code, or {@code null}.
     */
    Board board(String code);

    /**
     * Create a new board with the specified {@code code} and {@code name}.
     * <p>This is only applicable to sites with a board type other than DYNAMIC.</p>
     *
     * @param name the name of the board.
     * @param code the code to create the board with.
     * @return the created board.
     */
    Board createBoard(String name, String code);

    ChanReader chanReader();

    void post(Reply reply, PostListener postListener);

    interface PostListener {

        void onPostComplete(HttpCall httpCall, ReplyResponse replyResponse);

        void onPostError(HttpCall httpCall);

    }

    boolean postRequiresAuthentication();

    /**
     * If {@link ReplyResponse#requireAuthentication} was {@code true}, or if
     * {@link #postRequiresAuthentication()} is {@code true}, get the authentication
     * required to post.
     * <p>
     * <p>Some sites know beforehand if you need to authenticate, some sites only report it
     * after posting. That's why there are two methods.</p>
     *
     * @return an {@link Authentication} model that describes the way to authenticate.
     */
    Authentication postAuthenticate();

    void delete(DeleteRequest deleteRequest, DeleteListener deleteListener);

    interface DeleteListener {
        void onDeleteComplete(HttpCall httpCall, DeleteResponse deleteResponse);

        void onDeleteError(HttpCall httpCall);
    }

    /* TODO(multi-site) this login mechanism is probably not generic enough right now,
     * especially if we're thinking about what a login really is
     * We'll expand this later when we have a better idea of what other sites require.
     */
    void login(LoginRequest loginRequest, LoginListener loginListener);

    void logout();

    boolean isLoggedIn();

    LoginRequest getLoginDetails();

    interface LoginListener {
        void onLoginComplete(HttpCall httpCall, LoginResponse loginResponse);

        void onLoginError(HttpCall httpCall);
    }
}
