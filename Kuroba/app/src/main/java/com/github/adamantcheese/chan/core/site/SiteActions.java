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

import com.github.adamantcheese.chan.core.model.InternalSiteArchive;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

public interface SiteActions {
    void boards(NetUtilsClasses.ResponseResult<Boards> boardsListener);

    void pages(Board board, NetUtilsClasses.ResponseResult<ChanPages> pagesListener);

    void post(Loadable loadableWithDraft, PostListener postListener);

    interface PostListener
            extends NetUtilsClasses.ResponseResult<ReplyResponse>, ProgressRequestBody.ProgressRequestListener {}

    boolean postRequiresAuthentication();

    /**
     * If {@link ReplyResponse#requireAuthentication} was {@code true}, or if
     * {@link #postRequiresAuthentication()} is {@code true}, get the authentication
     * required to post.
     * <p>
     * <p>Some sites know beforehand if you need to authenticate, some sites only report it
     * after posting. That's why there are two methods.</p>
     *
     * @param loadableWithDraft The draft reply with info possibly necessary for auth.
     * @return an {@link SiteAuthentication} model that describes the way to authenticate.
     */
    SiteAuthentication postAuthenticate(Loadable loadableWithDraft);

    void delete(DeleteRequest deleteRequest, NetUtilsClasses.ResponseResult<DeleteResponse> deleteListener);

    void archive(Board board, NetUtilsClasses.ResponseResult<InternalSiteArchive> archiveListener);

    void login(LoginRequest loginRequest, NetUtilsClasses.ResponseResult<LoginResponse> loginListener);

    void logout(NetUtilsClasses.ResponseResult<LoginResponse> loginListener);

    boolean isLoggedIn();

    LoginRequest getLoginDetails();
}
