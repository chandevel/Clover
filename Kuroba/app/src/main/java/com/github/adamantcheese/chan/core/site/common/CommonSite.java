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
package com.github.adamantcheese.chan.core.site.common;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.InternalSiteArchive;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.MainThreadResponseResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteBase;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteUrlHandler;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

/**
 * Your base site implementation; take a look at {@link #initialize(int, JsonSettings)} for the exact items you need to specify.
 * Do note that {@link #setName(String)} and {@link #setIcon(SiteIcon)} should be called in the constructor, otherwise site selection will break.
 * This is an optimization on the site selector that prevents extra work from being done.
 */
public abstract class CommonSite
        extends SiteBase {
    private String name;
    private SiteIcon icon;
    private BoardsType boardsType;
    private CommonConfig config;
    private CommonSiteUrlHandler resolvable;
    private CommonEndpoints endpoints;
    private CommonActions actions;
    private CommonApi api;

    public PostParser postParser;

    private final Boards staticBoards = new Boards();

    @Override
    public void initialize(int id, JsonSettings userSettings) {
        super.initialize(id, userSettings);
        setup();

        if ("App Setup".equals(name)) return; // for this special site, we don't need any of the rest of the items

        if (name == null) {
            throw new NullPointerException("setName not called");
        }

        if (icon == null) {
            throw new NullPointerException("setIcon not called");
        }

        if (boardsType == null) {
            throw new NullPointerException("setBoardsType not called");
        }

        if (this.config == null) {
            throw new NullPointerException("setConfig not called");
        }

        if (resolvable == null) {
            throw new NullPointerException("setResolvable not called");
        }

        if (endpoints == null) {
            throw new NullPointerException("setEndpoints not called");
        }

        if (actions == null) {
            throw new NullPointerException("setActions not called");
        }

        if (api == null) {
            throw new NullPointerException("setApi not called");
        }

        if (postParser == null) {
            throw new NullPointerException("setParser not called");
        }
    }

    public abstract void setup();

    /**
     * Call this in your constructor!
     *
     * @param name This site's name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Call this in your constructor!
     *
     * @param icon The favicon for this site; be sure that this is a singleton for efficiency.
     */
    public void setIcon(SiteIcon icon) {
        this.icon = icon;
        icon.get(ico -> {});
    }

    public void setBoardsType(BoardsType boardsType) {
        this.boardsType = boardsType;
    }

    public void setBoards(Board... boards) {
        boardsType = BoardsType.STATIC;
        Collections.addAll(staticBoards, boards);
    }

    public void setConfig(CommonConfig config) {
        this.config = config;
    }

    public void setResolvable(CommonSiteUrlHandler resolvable) {
        this.resolvable = resolvable;
    }

    public void setEndpoints(CommonEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    public void setActions(CommonActions actions) {
        this.actions = actions;
    }

    public void setApi(CommonApi api) {
        this.api = api;
    }

    public void setParser(CommentParser commentParser) {
        postParser = new DefaultPostParser(commentParser);
    }

    /*
     * Site implementation:
     */

    @Override
    public String name() {
        return name;
    }

    @Override
    public SiteIcon icon() {
        return icon;
    }

    @Override
    public BoardsType boardsType() {
        return boardsType;
    }

    @Override
    public SiteUrlHandler resolvable() {
        return resolvable;
    }

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return config.siteFeature(siteFeature);
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        return config.boardFeature(boardFeature, board);
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public SiteActions actions() {
        return actions;
    }

    @Override
    public ChanReader chanReader() {
        return api;
    }

    public abstract static class CommonConfig {
        @CallSuper
        public boolean siteFeature(SiteFeature siteFeature) {
            return siteFeature == SiteFeature.IMAGE_FILE_HASH;
        }

        public boolean boardFeature(BoardFeature boardFeature, Board board) {
            switch (boardFeature) {
                case POSTING_IMAGE:
                    return true;
                case POSTING_SPOILER:
                    return board.spoilers;
                default:
                    return false;
            }
        }
    }

    public static abstract class CommonSiteUrlHandler
            implements SiteUrlHandler {
        public abstract HttpUrl getUrl();

        public abstract String[] getMediaHosts();

        public abstract String[] getNames();

        @Override
        public boolean matchesName(String value) {
            for (String s : getNames()) {
                if (value.equals(s)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean matchesMediaHost(@NonNull HttpUrl url) {
            return SiteUrlHandler.containsMediaHostUrl(url, getMediaHosts());
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            return getUrl().host().equals(url.host());
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode)
                        .addPathSegment("res")
                        .addPathSegment(String.valueOf(loadable.no))
                        .toString();
            } else {
                return getUrl().toString();
            }
        }

        @Override
        public Loadable resolveLoadable(Site site, HttpUrl url) {
            StringBuilder urlPath = new StringBuilder();
            //noinspection KotlinInternalInJava
            HttpUrl.Companion.toPathString$okhttp(url.pathSegments(), urlPath);

            Matcher board = boardPattern().matcher(urlPath);
            Matcher thread = threadPattern().matcher(urlPath);

            try {
                if (thread.find()) {
                    Board b = site.board(thread.group(1));
                    if (b == null) {
                        return null;
                    }
                    Loadable l = Loadable.forThread(b, Integer.parseInt(thread.group(2)), "");

                    if (!isEmpty(url.fragment())) {
                        l.markedNo = Integer.parseInt(url.fragment());
                    }

                    return l;
                } else if (board.find()) {
                    Board b = site.board(board.group(1));
                    if (b == null) {
                        return null;
                    }

                    return Loadable.forCatalog(b);
                }
            } catch (NumberFormatException ignored) {
            }

            return null;
        }

        public Pattern boardPattern() {
            return Pattern.compile("/(\\w+)");
        }

        public Pattern threadPattern() {
            return Pattern.compile("/(\\w+)/\\w+/(\\d+).*");
        }
    }

    public static abstract class CommonEndpoints
            implements SiteEndpoints {
        protected CommonSite site;

        public CommonEndpoints(CommonSite site) {
            this.site = site;
        }

        @NonNull
        public SimpleHttpUrl from(String url) {
            return new SimpleHttpUrl(url);
        }

        @Override
        public HttpUrl catalog(Board board) {
            return null;
        }

        @Override
        public HttpUrl thread(Loadable loadable) {
            return null;
        }

        @Override
        public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
            return null;
        }

        @Override
        public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            return null;
        }

        @Override
        public Pair<HttpUrl, PassthroughBitmapResult> icon(ICON_TYPE icon, Map<String, String> arg) {
            return null;
        }

        @Override
        public HttpUrl boards() {
            return null;
        }

        @Override
        public HttpUrl archive(Board board) {
            return null;
        }

        @Override
        public HttpUrl reply(Loadable thread) {
            return null;
        }

        @Override
        public HttpUrl delete(Post post) {
            return null;
        }

        @Override
        public HttpUrl report(Post post) {
            return null;
        }

        @Override
        public HttpUrl login() {
            return null;
        }
    }

    public static class SimpleHttpUrl {
        @NonNull
        public HttpUrl.Builder url;

        public SimpleHttpUrl(String from) {
            HttpUrl res = HttpUrl.parse(from);
            if (res == null) {
                throw new NullPointerException();
            }
            url = res.newBuilder();
        }

        public SimpleHttpUrl(@NonNull HttpUrl.Builder from) {
            url = from;
        }

        public SimpleHttpUrl builder() {
            return new SimpleHttpUrl(url.build().newBuilder());
        }

        public SimpleHttpUrl s(String segment) {
            url.addPathSegment(segment);
            return this;
        }

        public HttpUrl url() {
            return url.build();
        }
    }

    public static abstract class CommonActions
            implements SiteActions {
        protected CommonSite site;

        public CommonActions(CommonSite site) {
            this.site = site;
        }

        @Override
        public void post(Loadable loadableWithDraft, PostListener postListener) {
            MultipartHttpCall<ReplyResponse> call =
                    new MultipartHttpCall<ReplyResponse>(new MainThreadResponseResult<>(postListener)) {
                        @Override
                        public ReplyResponse convert(Response response) {
                            return handlePost(loadableWithDraft, response);
                        }
                    };

            call.url(site.endpoints().reply(loadableWithDraft));

            prepare(call, loadableWithDraft, new ResponseResult<Void>() {
                @Override
                public void onFailure(Exception e) {}

                @Override
                public void onSuccess(Void result) {
                    setupPost(loadableWithDraft, call);
                    NetUtils.makeHttpCall(call);
                }
            });
        }

        public void setupPost(Loadable loadable, MultipartHttpCall<ReplyResponse> call) {
        }

        public abstract ReplyResponse handlePost(Loadable loadable, Response httpResponse);

        @Override
        public boolean postRequiresAuthentication() {
            return false;
        }

        @Override
        public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
            return SiteAuthentication.fromNone();
        }

        public void prepare(
                MultipartHttpCall<ReplyResponse> call,
                Loadable loadable,
                NetUtilsClasses.ResponseResult<Void> extraHeaders
        ) {
            // by default, no class needs any extra headers so just invoke the result immediately
            extraHeaders.onSuccess(null);
        }

        @Override
        public void delete(DeleteRequest deleteRequest, ResponseResult<DeleteResponse> deleteListener) {
            MultipartHttpCall<DeleteResponse> call =
                    new MultipartHttpCall<DeleteResponse>(new MainThreadResponseResult<>(deleteListener)) {
                        @Override
                        public DeleteResponse convert(Response response)
                                throws IOException {
                            return handleDelete(response);
                        }
                    };

            call.url(site.endpoints().delete(deleteRequest.post));
            setupDelete(deleteRequest, call);
            NetUtils.makeHttpCall(call);
        }

        public void setupDelete(DeleteRequest deleteRequest, MultipartHttpCall<DeleteResponse> call) {
        }

        public DeleteResponse handleDelete(Response httpResponse)
                throws IOException {
            return new DeleteResponse();
        }

        @Override
        public void boards(ResponseResult<Boards> boardsListener) {
            boardsListener.onSuccess(new Boards(site.staticBoards));
        }

        @Override
        public void pages(Board board, ResponseResult<ChanPages> pagesListener) {
            pagesListener.onSuccess(new ChanPages());
        }

        @Override
        public void archive(Board board, ResponseResult<InternalSiteArchive> archiveListener) {
        }

        @Override
        public void login(LoginRequest loginRequest, ResponseResult<LoginResponse> loginListener) {
        }

        @Override
        public void logout(final ResponseResult<LoginResponse> loginListener) {
        }

        @Override
        public boolean isLoggedIn() {
            return false;
        }

        @Override
        public LoginRequest getLoginDetails() {
            return null;
        }
    }

    public static abstract class CommonApi
            implements ChanReader {
        protected CommonSite site;

        public CommonApi(CommonSite site) {
            this.site = site;
        }

        @Override
        public PostParser getParser() {
            return site.postParser;
        }
    }
}
