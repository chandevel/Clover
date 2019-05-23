/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.site;

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Archive;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;

import java.util.List;

public interface SiteActions {
    void boards(BoardsListener boardsListener);

    void pages(Board board, PagesListener pagesListener);

    void archives(ArchiveRequestListener archivesListener);

    interface BoardsListener {
        void onBoardsReceived(SiteBase.Boards boards);
    }

    interface PagesListener {
        void onPagesReceived(Board b, Chan4PagesRequest.Pages pages);
    }

    interface ArchiveRequestListener {
        void onArchivesReceived(List<ArchivesManager.Archives> archives);
    }

    void post(Reply reply, PostListener postListener);

    interface PostListener {
        void onPostComplete(HttpCall httpCall, ReplyResponse replyResponse);

        void onUploadingProgress(int percent);

        void onPostError(HttpCall httpCall, Exception exception);
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
     * @return an {@link SiteAuthentication} model that describes the way to authenticate.
     */
    SiteAuthentication postAuthenticate();

    void delete(DeleteRequest deleteRequest, DeleteListener deleteListener);

    interface DeleteListener {
        void onDeleteComplete(HttpCall httpCall, DeleteResponse deleteResponse);

        void onDeleteError(HttpCall httpCall);
    }

    void archive(Board board, ArchiveListener archiveListener);

    interface ArchiveListener {
        void onArchive(Archive archive);

        void onArchiveError();
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
