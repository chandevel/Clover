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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import android.util.JsonReader;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.JsonReaderRequest;
import com.github.adamantcheese.chan.core.settings.OptionSettingItem;
import com.github.adamantcheese.chan.core.settings.OptionsSetting;
import com.github.adamantcheese.chan.core.settings.SettingProvider;
import com.github.adamantcheese.chan.core.settings.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.core.settings.StringSetting;
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteBase;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteRequestModifier;
import com.github.adamantcheese.chan.core.site.SiteSetting;
import com.github.adamantcheese.chan.core.site.SiteUrlHandler;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.common.FutabaChanReader;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import okhttp3.HttpUrl;
import okhttp3.Request;

import static com.github.adamantcheese.chan.core.site.sites.chan4.Chan4.CaptchaType.V2JS;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getPreferences;

public class Chan4
        extends SiteBase {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;

    private static final String TAG = "Chan4";
    private static final String CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
    private static final Random random = new Random();

    public static final SiteUrlHandler URL_HANDLER = new SiteUrlHandler() {

        private final String[] mediaHosts = new String[]{"i.4cdn.org"};

        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan4.class;
        }

        @Override
        public boolean matchesMediaHost(@NonNull HttpUrl url) {
            return SiteBase.containsMediaHostUrl(url, mediaHosts);
        }

        @Override
        public boolean matchesName(String value) {
            return value.equals("4chan");
        }

        @Override
        public boolean respondsTo(HttpUrl url) {
            String host = url.host();

            return host.equals("4chan.org") || host.equals("www.4chan.org") || host.equals("boards.4chan.org")
                    || host.equals("4channel.org") || host.equals("www.4channel.org") || host.equals(
                    "boards.4channel.org");
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isCatalogMode()) {
                if (postNo > 0) {
                    return "https://boards.4chan.org/" + loadable.board.code + "/thread/" + postNo;
                } else {
                    return "https://boards.4chan.org/" + loadable.board.code + "/";
                }
            } else if (loadable.isThreadMode()) {
                String url = "https://boards.4chan.org/" + loadable.board.code + "/thread/" + loadable.no;
                if (postNo > 0 && loadable.no != postNo) {
                    url += "#p" + postNo;
                }
                return url;
            } else {
                return "https://boards.4chan.org/" + loadable.board.code + "/";
            }
        }

        @Override
        public Loadable resolveLoadable(Site site, HttpUrl url) {
            List<String> parts = url.pathSegments();

            if (!parts.isEmpty()) {
                String boardCode = parts.get(0);
                Board board = site.board(boardCode);
                if (board != null) {
                    if (parts.size() < 3) {
                        // Board mode
                        return Loadable.forCatalog(board);
                    } else {
                        // Thread mode
                        int no = -1;
                        try {
                            no = Integer.parseInt(parts.get(2));
                        } catch (NumberFormatException ignored) {
                        }

                        int post = -1;
                        String fragment = url.fragment();
                        if (fragment != null) {
                            int index = fragment.indexOf("p");
                            if (index >= 0) {
                                try {
                                    post = Integer.parseInt(fragment.substring(index + 1));
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }

                        if (no >= 0) {
                            Loadable loadable = Loadable.forThread(site, board, no, "");
                            if (post >= 0) {
                                loadable.markedNo = post;
                            }

                            return loadable;
                        }
                    }
                }
            }

            return null;
        }
    };

    private final SiteEndpoints endpoints = new SiteEndpoints() {
        private final HttpUrl a = new HttpUrl.Builder().scheme("https").host("a.4cdn.org").build();
        private final HttpUrl i = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
        private final HttpUrl t = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
        private final HttpUrl s = new HttpUrl.Builder().scheme("https").host("s.4cdn.org").build();
        private final HttpUrl sys = new HttpUrl.Builder().scheme("https").host("sys.4chan.org").build();
        private final HttpUrl b = new HttpUrl.Builder().scheme("https").host("boards.4chan.org").build();

        @Override
        public HttpUrl catalog(Board board) {
            return a.newBuilder().addPathSegment(board.code).addPathSegment("catalog.json").build();
        }

        @Override
        public HttpUrl thread(Board board, Loadable loadable) {
            return a.newBuilder()
                    .addPathSegment(board.code)
                    .addPathSegment("thread")
                    .addPathSegment(loadable.no + ".json")
                    .build();
        }

        @Override
        public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
            String imageFile = arg.get("tim") + "." + arg.get("ext");
            return i.newBuilder().addPathSegment(post.board.code).addPathSegment(imageFile).build();
        }

        @Override
        public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
            if (spoiler) {
                HttpUrl.Builder image = s.newBuilder().addPathSegment("image");
                if (post.board.customSpoilers >= 0) {
                    int i = random.nextInt(post.board.customSpoilers) + 1;
                    image.addPathSegment("spoiler-" + post.board.code + i + ".png");
                } else {
                    image.addPathSegment("spoiler.png");
                }
                return image.build();
            } else {
                return t.newBuilder().addPathSegment(post.board.code).addPathSegment(arg.get("tim") + "s.jpg").build();
            }
        }

        @Override
        public HttpUrl icon(String icon, Map<String, String> arg) {
            HttpUrl.Builder b = s.newBuilder().addPathSegment("image");

            switch (icon) {
                case "country":
                    b.addPathSegment("country");
                    b.addPathSegment(arg.get("country_code").toLowerCase(Locale.ENGLISH) + ".gif");
                    break;
                case "troll_country":
                    b.addPathSegment("country");
                    b.addPathSegment("troll");
                    b.addPathSegment(arg.get("troll_country_code").toLowerCase(Locale.ENGLISH) + ".gif");
                    break;
                case "since4pass":
                    b.addPathSegment("minileaf.gif");
                    break;
            }

            return b.build();
        }

        @Override
        public HttpUrl boards() {
            return a.newBuilder().addPathSegment("boards.json").build();
        }

        @Override
        public HttpUrl pages(Board board) {
            return a.newBuilder().addPathSegment(board.code).addPathSegment("threads.json").build();
        }

        @Override
        public HttpUrl archive(Board board) {
            return b.newBuilder().addPathSegment(board.code).addPathSegment("archive").build();
        }

        @Override
        public HttpUrl reply(Loadable loadable) {
            return sys.newBuilder().addPathSegment(loadable.board.code).addPathSegment("post").build();
        }

        @Override
        public HttpUrl delete(Post post) {
            return sys.newBuilder().addPathSegment(post.board.code).addPathSegment("imgboard.php").build();
        }

        @Override
        public HttpUrl report(Post post) {
            return sys.newBuilder()
                    .addPathSegment(post.board.code)
                    .addPathSegment("imgboard.php")
                    .addQueryParameter("mode", "report")
                    .addQueryParameter("no", String.valueOf(post.no))
                    .build();
        }

        @Override
        public HttpUrl login() {
            return sys.newBuilder().addPathSegment("auth").build();
        }
    };

    private SiteRequestModifier siteRequestModifier = new SiteRequestModifier() {
        @Override
        public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
            if (actions.isLoggedIn()) {
                requestBuilder.addHeader("Cookie", "pass_id=" + passToken.get());
            }
        }

        @Override
        public void modifyWebView(WebView webView) {
            final HttpUrl sys = new HttpUrl.Builder().scheme("https").host("sys.4chan.org").build();

            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.removeAllCookies(null);
            if (actions.isLoggedIn()) {
                String[] passCookies = {"pass_enabled=1;", "pass_id=" + passToken.get() + ";"};
                String domain = sys.scheme() + "://" + sys.host() + "/";
                for (String cookie : passCookies) {
                    cookieManager.setCookie(domain, cookie);
                }
            }
        }
    };

    private SiteActions actions = new SiteActions() {
        @Override
        public void boards(final BoardsListener listener) {
            requestQueue.add(new Chan4BoardsRequest(Chan4.this,
                    response -> listener.onBoardsReceived(new Boards(response)),
                    error -> {
                        Logger.e(TAG, "Failed to get boards from server", error);
                        listener.onBoardsReceived(new Boards(new ArrayList<>()));
                    }
            ));
        }

        @Override
        public void pages(Board board, PagesListener listener) {
            requestQueue.add(new Chan4PagesRequest(Chan4.this,
                    board,
                    response -> listener.onPagesReceived(board, new Chan4PagesRequest.Pages(response)),
                    error -> {
                        Logger.e(TAG, "Failed to get pages for board " + board.code);
                        listener.onPagesReceived(board, new Chan4PagesRequest.Pages(new ArrayList<>()));
                    }
            ));
        }

        @Override
        public void archives(ArchiveRequestListener archivesListener) {
            requestQueue.add(new JsonReaderRequest<List<ArchivesManager.Archives>>(
                    "https://nstepien.github.io/archives.json/archives.json",
                    archivesListener::onArchivesReceived,
                    error -> Logger.e(TAG, "Failed to get archives for 4Chan, using builtins")
            ) {
                @Override
                public List<ArchivesManager.Archives> readJson(JsonReader reader)
                        throws Exception {
                    return ArchivesManager.parseArchives(reader);
                }
            });
        }

        @Override
        public void archive(Board board, ArchiveListener archiveListener) {
            requestQueue.add(new Chan4ArchiveRequest(Chan4.this,
                    board,
                    archiveListener::onArchive,
                    error -> archiveListener.onArchiveError()
            ));
        }

        @Override
        public void post(Reply reply, final PostListener postListener) {
            httpCallManager.makeHttpCall(new Chan4ReplyCall(Chan4.this, reply),
                    new HttpCall.HttpCallback<CommonReplyHttpCall>() {
                        @Override
                        public void onHttpSuccess(CommonReplyHttpCall httpPost) {
                            postListener.onPostComplete(httpPost, httpPost.replyResponse);
                        }

                        @Override
                        public void onHttpFail(CommonReplyHttpCall httpPost, Exception e) {
                            postListener.onPostError(httpPost, e);
                        }
                    },
                    postListener::onUploadingProgress
            );
        }

        @Override
        public boolean postRequiresAuthentication() {
            return !isLoggedIn();
        }

        @Override
        public SiteAuthentication postAuthenticate() {
            if (isLoggedIn()) {
                return SiteAuthentication.fromNone();
            } else {
                switch (captchaType.get()) {
                    case V2JS:
                        return SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, "https://boards.4chan.org");
                    case V2NOJS:
                        return SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, "https://boards.4chan.org");
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }

        @Override
        public void delete(DeleteRequest deleteRequest, final DeleteListener deleteListener) {
            httpCallManager.makeHttpCall(new Chan4DeleteHttpCall(Chan4.this, deleteRequest),
                    new HttpCall.HttpCallback<Chan4DeleteHttpCall>() {
                        @Override
                        public void onHttpSuccess(Chan4DeleteHttpCall httpPost) {
                            deleteListener.onDeleteComplete(httpPost, httpPost.deleteResponse);
                        }

                        @Override
                        public void onHttpFail(Chan4DeleteHttpCall httpPost, Exception e) {
                            deleteListener.onDeleteError(httpPost);
                        }
                    }
            );
        }

        @Override
        public void login(LoginRequest loginRequest, final LoginListener loginListener) {
            passUser.set(loginRequest.user);
            passPass.set(loginRequest.pass);

            httpCallManager.makeHttpCall(new Chan4PassHttpCall(Chan4.this, loginRequest),
                    new HttpCall.HttpCallback<Chan4PassHttpCall>() {
                        @Override
                        public void onHttpSuccess(Chan4PassHttpCall httpCall) {
                            LoginResponse loginResponse = httpCall.loginResponse;
                            if (loginResponse.success) {
                                passToken.set(loginResponse.token);
                            }
                            loginListener.onLoginComplete(httpCall, loginResponse);
                        }

                        @Override
                        public void onHttpFail(Chan4PassHttpCall httpCall, Exception e) {
                            loginListener.onLoginError(httpCall);
                        }
                    }
            );
        }

        @Override
        public void logout() {
            passToken.set("");
        }

        @Override
        public boolean isLoggedIn() {
            return !passToken.get().isEmpty();
        }

        @Override
        public LoginRequest getLoginDetails() {
            return new LoginRequest(passUser.get(), passPass.get());
        }
    };

    // Legacy settings that were global before
    private final StringSetting passUser;
    private final StringSetting passPass;
    private final StringSetting passToken;

    public enum CaptchaType
            implements OptionSettingItem {
        V2JS("v2js"),
        V2NOJS("v2nojs");

        String name;

        CaptchaType(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }
    }

    private OptionsSetting<CaptchaType> captchaType;

    public Chan4() {
        // we used these before multisite, and lets keep using them.
        SettingProvider p = new SharedPreferencesSettingProvider(getPreferences());
        passUser = new StringSetting(p, "preference_pass_token", "");
        passPass = new StringSetting(p, "preference_pass_pin", "");
        // token was renamed, before it meant the username, now it means the token returned
        // from the server that the cookie is set to.
        passToken = new StringSetting(p, "preference_pass_id", "");

        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void initializeSettings() {
        super.initializeSettings();

        captchaType = new OptionsSetting<>(settingsProvider, "preference_captcha_type_chan4", CaptchaType.class, V2JS);
    }

    @Override
    public List<SiteSetting> settings() {
        return Collections.singletonList(SiteSetting.forOption(captchaType,
                "Captcha type",
                Arrays.asList("Javascript", "Noscript")
        ));
    }

    @Override
    public String name() {
        return "4chan";
    }

    @Override
    public SiteIcon icon() {
        return SiteIcon.fromFavicon(HttpUrl.parse("https://s.4cdn.org/image/favicon.ico"));
    }

    @Override
    public SiteUrlHandler resolvable() {
        return URL_HANDLER;
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
            case ARCHIVE:
                return board.archive;
            default:
                return false;
        }
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public SiteRequestModifier requestModifier() {
        return siteRequestModifier;
    }

    @Override
    public ChanReader chanReader() {
        return new FutabaChanReader();
    }

    @Override
    public SiteActions actions() {
        return actions;
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return chunkDownloaderSiteProperties;
    }
}
