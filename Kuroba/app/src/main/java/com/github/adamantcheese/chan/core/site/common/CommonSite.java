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

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteBase;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteRequestModifier;
import com.github.adamantcheese.chan.core.site.SiteUrlHandler;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4PagesRequest;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public abstract class CommonSite
        extends SiteBase {
    private final Random secureRandom = new SecureRandom();

    private String name;
    private SiteIcon icon;
    private BoardsType boardsType;
    private CommonConfig config;
    private CommonSiteUrlHandler resolvable;
    private CommonEndpoints endpoints;
    private CommonActions actions;
    private CommonApi api;
    private CommonRequestModifier requestModifier;

    public PostParser postParser;

    private List<Board> staticBoards = new ArrayList<>();

    @Override
    public void initialize(int id, SiteConfig config, JsonSettings userSettings) {
        super.initialize(id, config, userSettings);
        setup();

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

        if (requestModifier == null) {
            // No-op implementation.
            requestModifier = new CommonRequestModifier() {};
        }
    }

    public abstract void setup();

    public void setName(String name) {
        this.name = name;
    }

    public void setIcon(SiteIcon icon) {
        this.icon = icon;
    }

    public void setBoardsType(BoardsType boardsType) {
        this.boardsType = boardsType;
    }

    public void setBoards(Board... boards) {
        boardsType = BoardsType.STATIC;
        staticBoards.addAll(Arrays.asList(boards));
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
    public boolean feature(Feature feature) {
        return config.feature(feature);
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
    public SiteRequestModifier requestModifier() {
        return requestModifier;
    }

    @Override
    public ChanReader chanReader() {
        return api;
    }

    public abstract class CommonConfig {
        public boolean feature(Feature feature) {
            return false;
        }

        public boolean boardFeature(BoardFeature boardFeature, Board board) {
            return false;
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
            return SiteBase.containsMediaHostUrl(url, getMediaHosts());
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
            Matcher board = boardPattern().matcher(url.encodedPath());
            Matcher thread = threadPattern().matcher(url.encodedPath());

            try {
                if (thread.find()) {
                    Board b = site.board(thread.group(1));
                    if (b == null) {
                        return null;
                    }
                    Loadable l = Loadable.forThread(site, b, Integer.parseInt(thread.group(3)), "");

                    if (isEmpty(url.fragment())) {
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
        public HttpUrl thread(Board board, Loadable loadable) {
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
        public HttpUrl icon(String icon, Map<String, String> arg) {
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
        public void post(Reply reply, PostListener postListener) {
            ReplyResponse replyResponse = new ReplyResponse();

            reply.password = Long.toHexString(site.secureRandom.nextLong());
            replyResponse.password = reply.password;
            replyResponse.siteId = reply.loadable.siteId;
            replyResponse.boardCode = reply.loadable.boardCode;

            MultipartHttpCall call = new MultipartHttpCall(site) {
                @Override
                public void process(Response response, String result) {
                    handlePost(replyResponse, response, result);
                }
            };

            call.url(site.endpoints().reply(reply.loadable));

            if (requirePrepare()) {
                Handler handler = new Handler(Looper.getMainLooper());
                new Thread(() -> {
                    prepare(call, reply, replyResponse);
                    handler.post(() -> {
                        setupPost(reply, call);
                        makePostCall(call, replyResponse, postListener);
                    });
                }).start();
            } else {
                setupPost(reply, call);
                makePostCall(call, replyResponse, postListener);
            }
        }

        public void setupPost(Reply reply, MultipartHttpCall call) {
        }

        public void handlePost(ReplyResponse response, Response httpResponse, String responseBody) {
        }

        @Override
        public boolean postRequiresAuthentication() {
            return false;
        }

        @Override
        public SiteAuthentication postAuthenticate() {
            return SiteAuthentication.fromNone();
        }

        private void makePostCall(HttpCall call, ReplyResponse replyResponse, PostListener postListener) {
            site.httpCallManager.makeHttpCall(call, new HttpCall.HttpCallback<HttpCall>() {
                @Override
                public void onHttpSuccess(HttpCall httpCall) {
                    postListener.onPostComplete(httpCall, replyResponse);
                }

                @Override
                public void onHttpFail(HttpCall httpCall, Exception e) {
                    postListener.onPostError(httpCall, e);
                }
            });
        }

        public boolean requirePrepare() {
            return false;
        }

        public void prepare(MultipartHttpCall call, Reply reply, ReplyResponse replyResponse) {
        }

        @Override
        public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {
            DeleteResponse deleteResponse = new DeleteResponse();

            MultipartHttpCall call = new MultipartHttpCall(site) {
                @Override
                public void process(Response response, String result) {
                    handleDelete(deleteResponse, response, result);
                }
            };

            call.url(site.endpoints().delete(deleteRequest.post));
            setupDelete(deleteRequest, call);
            site.httpCallManager.makeHttpCall(call, new HttpCall.HttpCallback<HttpCall>() {
                @Override
                public void onHttpSuccess(HttpCall httpCall) {
                    deleteListener.onDeleteComplete(httpCall, deleteResponse);
                }

                @Override
                public void onHttpFail(HttpCall httpCall, Exception e) {
                    deleteListener.onDeleteError(httpCall);
                }
            });
        }

        public void setupDelete(DeleteRequest deleteRequest, MultipartHttpCall call) {
        }

        public void handleDelete(DeleteResponse response, Response httpResponse, String responseBody) {
        }

        @Override
        public void boards(BoardsListener boardsListener) {
            if (!site.staticBoards.isEmpty()) {
                boardsListener.onBoardsReceived(new Boards(site.staticBoards));
            }
        }

        @Override
        public void pages(Board board, PagesListener pagesListener) {
            pagesListener.onPagesReceived(board, new Chan4PagesRequest.Pages(new ArrayList<>()));
        }

        @Override
        public void archives(ArchiveRequestListener archivesListener) {
        }

        @Override
        public void archive(Board board, ArchiveListener archiveListener) {
        }

        @Override
        public void login(LoginRequest loginRequest, LoginListener loginListener) {
        }

        @Override
        public void logout() {
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

    public abstract class CommonRequestModifier
            implements SiteRequestModifier {
        @Override
        public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
        }

        @Override
        public void modifyWebView(WebView webView) {
        }
    }
}
