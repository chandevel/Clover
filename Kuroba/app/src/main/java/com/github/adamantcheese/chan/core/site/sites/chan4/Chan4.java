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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.InternalSiteArchive;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.primitives.OptionsSetting;
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting;
import com.github.adamantcheese.chan.core.settings.provider.SettingProvider;
import com.github.adamantcheese.chan.core.settings.provider.SharedPreferencesSettingProvider;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteBase;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteSetting;
import com.github.adamantcheese.chan.core.site.SiteUrlHandler;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.CaptchaType;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.common.FutabaChanReader;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.http.LoginResponse;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kotlin.random.Random;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Request;

import static com.github.adamantcheese.chan.core.site.common.CommonDataStructs.CaptchaType.V2NOJS;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getPreferences;

public class Chan4
        extends SiteBase {
    private ChanReader reader;

    public static final SiteUrlHandler URL_HANDLER = new SiteUrlHandler() {

        private final String[] mediaHosts = new String[]{"i.4cdn.org"};

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
            if (loadable.isThreadMode()) {
                String url = "https://boards.4chan.org/" + loadable.boardCode + "/thread/" + loadable.no;
                if (postNo > 0 && loadable.no != postNo) {
                    url += "#p" + postNo;
                }
                return url;
            } else {
                return "https://boards.4chan.org/" + loadable.boardCode + "/";
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
                            Loadable loadable = Loadable.forThread(board, no, "");
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

    private final HttpUrl a = new HttpUrl.Builder().scheme("https").host("a.4cdn.org").build();
    private final HttpUrl i = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
    private final HttpUrl t = new HttpUrl.Builder().scheme("https").host("i.4cdn.org").build();
    private final HttpUrl s = new HttpUrl.Builder().scheme("https").host("s.4cdn.org").build();
    private final HttpUrl sys = new HttpUrl.Builder().scheme("https").host("sys.4chan.org").build();
    private final HttpUrl b = new HttpUrl.Builder().scheme("https").host("boards.4chan.org").build();

    private final SiteEndpoints endpoints = new SiteEndpoints() {
        @Override
        public HttpUrl catalog(Board board) {
            return a.newBuilder().addPathSegment(board.code).addPathSegment("catalog.json").build();
        }

        @Override
        public HttpUrl thread(Loadable loadable) {
            return a.newBuilder()
                    .addPathSegment(loadable.boardCode)
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
                    int i = Random.Default.nextInt(post.board.customSpoilers) + 1;
                    image.addPathSegment("spoiler-" + post.board.code + i + ".png");
                } else {
                    image.addPathSegment("spoiler.png");
                }
                return image.build();
            } else {
                if ("swf".equals(arg.get("ext"))) {
                    return HttpUrl.parse(BuildConfig.RESOURCES_ENDPOINT + "swf_thumb.png");
                }
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
            return sys.newBuilder().addPathSegment(loadable.boardCode).addPathSegment("post").build();
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

    private final SiteActions actions = new SiteActions() {
        @Override
        public void boards(final ResponseResult<Boards> listener) {
            NetUtils.makeJsonRequest(endpoints.boards(),
                    listener,
                    new Chan4BoardsRequest(Chan4.this),
                    NetUtilsClasses.ONE_DAY_CACHE
            );
        }

        @Override
        public void pages(Board board, ResponseResult<ChanPages> listener) {
            NetUtils.makeJsonRequest(endpoints().pages(board), new ResponseResult<ChanPages>() {
                @Override
                public void onFailure(Exception e) {
                    Logger.e(Chan4.this, "Failed to get pages for board " + board.code, e);
                    listener.onSuccess(new ChanPages());
                }

                @Override
                public void onSuccess(ChanPages result) {
                    listener.onSuccess(result);
                }
            }, new Chan4PagesParser(), NetUtilsClasses.NO_CACHE);
        }

        @Override
        public void archive(Board board, ResponseResult<InternalSiteArchive> archiveListener) {
            NetUtils.makeHTMLRequest(endpoints().archive(board),
                    new NetUtilsClasses.MainThreadResponseResult<>(archiveListener),
                    response -> {
                        List<InternalSiteArchive.ArchiveItem> items = new ArrayList<>();

                        Element table = response.getElementById("arc-list");
                        Element tableBody = table.getElementsByTag("tbody").first();
                        Elements trs = tableBody.getElementsByTag("tr");
                        for (Element tr : trs) {
                            Elements dataElements = tr.getElementsByTag("td");
                            String description = dataElements.get(1).text();
                            int id = Integer.parseInt(dataElements.get(0).text());
                            items.add(InternalSiteArchive.ArchiveItem.fromDescriptionId(description, id));
                        }

                        return InternalSiteArchive.fromItems(items);
                    },
                    NetUtilsClasses.NO_CACHE
            );
        }

        @Override
        public void post(Loadable loadableWithDraft, final PostListener postListener) {
            NetUtils.makeHttpCall(new Chan4ReplyCall(loadableWithDraft).setCallback(new ResponseResult<CommonReplyHttpCall>() {
                @Override
                public void onSuccess(CommonReplyHttpCall httpPost) {
                    postListener.onSuccess(httpPost.replyResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    postListener.onFailure(e);
                }
            }), postListener);
        }

        @Override
        public boolean postRequiresAuthentication() {
            return !isLoggedIn();
        }

        @Override
        public SiteAuthentication postAuthenticate() {
            final String CAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
            if (isLoggedIn()) {
                return SiteAuthentication.fromNone();
            } else {
                switch (captchaType.get()) {
                    case V2JS:
                        return SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, b.toString());
                    case V2NOJS:
                        return SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, b.toString());
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }

        @Override
        public void delete(DeleteRequest deleteRequest, final ResponseResult<DeleteResponse> deleteListener) {
            NetUtils.makeHttpCall(new Chan4DeleteHttpCall(Chan4.this,
                    deleteRequest
            ).setCallback(new ResponseResult<Chan4DeleteHttpCall>() {
                @Override
                public void onSuccess(Chan4DeleteHttpCall httpPost) {
                    deleteListener.onSuccess(httpPost.deleteResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    deleteListener.onFailure(e);
                }
            }));
        }

        @Override
        public void login(LoginRequest loginRequest, final ResponseResult<LoginResponse> loginListener) {
            passUser.set(loginRequest.user);
            passPass.set(loginRequest.pass);

            NetUtils.makeHttpCall(new Chan4PassHttpCall(Chan4.this,
                    loginRequest
            ).setCallback(new ResponseResult<Chan4PassHttpCall>() {
                @Override
                public void onSuccess(Chan4PassHttpCall httpCall) {
                    loginListener.onSuccess(httpCall.loginResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    loginListener.onFailure(e);
                }
            }));
        }

        @Override
        public void logout() {
            List<Cookie> currentCookies = NetUtils.applicationClient.cookieJar().loadForRequest(sys);
            List<Cookie> expireCookies = new ArrayList<>();
            for (Cookie c : currentCookies) {
                if (c.name().startsWith("pass")) {
                    expireCookies.add(new Cookie.Builder().domain(c.domain())
                            .name(c.name())
                            .value(c.value())
                            .path(c.path())
                            .expiresAt(0)
                            .build());
                }
            }
            NetUtils.applicationClient.cookieJar().saveFromResponse(sys, expireCookies);
        }

        @Override
        public boolean isLoggedIn() {
            for (Cookie cookie : NetUtils.applicationClient.cookieJar().loadForRequest(sys)) {
                if (cookie.name().equals("pass_id") && !cookie.value().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public LoginRequest getLoginDetails() {
            return new LoginRequest(passUser.get(), passPass.get());
        }
    };

    // Legacy settings that were global before
    private final StringSetting passUser;
    private final StringSetting passPass;

    private OptionsSetting<CaptchaType> captchaType;
    public static StringSetting flagType;

    public Chan4() {
        // we used these before multisite, and lets keep using them.
        SettingProvider<Object> p = new SharedPreferencesSettingProvider(getPreferences());
        passUser = new StringSetting(p, "preference_pass_token", "");
        passPass = new StringSetting(p, "preference_pass_pin", "");
        icon().get(icon -> {});
    }

    @Override
    public void initializeSettings() {
        super.initializeSettings();

        captchaType =
                new OptionsSetting<>(settingsProvider, "preference_captcha_type_chan4", CaptchaType.class, V2NOJS);
        flagType = new StringSetting(settingsProvider, "preference_flag_chan4", "0");
    }

    @Override
    public List<SiteSetting<?>> settings() {
        List<SiteSetting<?>> settings = new ArrayList<>();
        SiteSetting<?> captchaSetting =
                new SiteSetting<>("Captcha type", captchaType, Arrays.asList("Javascript", "Noscript"));
        settings.add(captchaSetting);
        settings.add(new SiteSetting<>("Country flag code", flagType, null));
        return settings;
    }

    @Override
    public String name() {
        return "4chan";
    }

    private final SiteIcon icon = SiteIcon.fromFavicon(HttpUrl.parse("https://s.4cdn.org/image/favicon.ico"));

    @Override
    public SiteIcon icon() {
        return icon;
    }

    @Override
    public SiteUrlHandler resolvable() {
        return URL_HANDLER;
    }

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return true; // everything is supported
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
                // only some boards have local archives
                return board.archive;
            case FORCED_ANONYMOUS:
                // some boards (like /b/) disable the name field
                return board.forcedAnon;
            default:
                return false;
        }
    }

    @Override
    public SiteEndpoints endpoints() {
        return endpoints;
    }

    @Override
    public ChanReader chanReader() {
        if (reader == null) {
            reader = new FutabaChanReader();
        }
        return reader;
    }

    @Override
    public SiteActions actions() {
        return actions;
    }
}
