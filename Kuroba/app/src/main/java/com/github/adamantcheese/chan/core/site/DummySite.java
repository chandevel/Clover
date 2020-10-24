package com.github.adamantcheese.chan.core.site;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.JsonReader;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Request;

public class DummySite
        implements Site {
    @Override
    public void initialize(int id, JsonSettings userSettings) {}

    @Override
    public void postInitialize() {}

    @Override
    public int id() {
        return Integer.MIN_VALUE;
    }

    @Override
    public String name() {
        return "Dummy";
    }

    @Override
    public SiteIcon icon() {
        return SiteIcon.fromDrawable(new BitmapDrawable(BitmapFactory.decodeResource(AndroidUtils.getAppContext()
                .getResources(), R.drawable.trash_icon)));
    }

    @Override
    public BoardsType boardsType() {
        return BoardsType.STATIC;
    }

    @Override
    public SiteUrlHandler resolvable() {
        return new SiteUrlHandler() {
            @Override
            public boolean matchesName(String value) {
                return false;
            }

            @Override
            public boolean respondsTo(HttpUrl url) {
                return false;
            }

            @Override
            public boolean matchesMediaHost(@NonNull HttpUrl url) {
                return false;
            }

            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                return "https://www.example.com";
            }

            @Override
            public Loadable resolveLoadable(Site site, HttpUrl url) {
                return Loadable.emptyLoadable();
            }
        };
    }

    @Override
    public boolean siteFeature(SiteFeature siteFeature) {
        return false;
    }

    @Override
    public boolean boardFeature(BoardFeature boardFeature, Board board) {
        return false;
    }

    @Override
    public List<SiteSetting<?>> settings() {
        return Collections.emptyList();
    }

    @Override
    public SiteEndpoints endpoints() {
        return new SiteEndpoints() {
            @Override
            public HttpUrl catalog(Board board) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl thread(Loadable loadable) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl icon(String icon, Map<String, String> arg) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl boards() {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl pages(Board board) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl archive(Board board) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl reply(Loadable thread) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl delete(Post post) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl report(Post post) {
                return HttpUrl.get("https://www.example.com");
            }

            @Override
            public HttpUrl login() {
                return HttpUrl.get("https://www.example.com");
            }
        };
    }

    @Override
    public CommonSite.CommonCallModifier callModifier() {
        return new CommonSite.CommonCallModifier() {
            @Override
            public void modifyHttpCall(HttpCall httpCall, Request.Builder requestBuilder) {
                super.modifyHttpCall(httpCall, requestBuilder);
            }

            @Override
            public void modifyWebView(WebView webView) {
                super.modifyWebView(webView);
            }
        };
    }

    @Override
    public ChanReader chanReader() {
        return new ChanReader() {
            @Override
            public PostParser getParser() {
                return new PostParser() {
                    @Override
                    public Post parse(
                            @NonNull Theme theme, Post.Builder builder, Callback callback
                    ) {
                        return null;
                    }
                };
            }

            @Override
            public void loadThread(JsonReader reader, ChanReaderProcessingQueue queue) {}

            @Override
            public void loadCatalog(JsonReader reader, ChanReaderProcessingQueue queue) {}

            @Override
            public void readPostObject(JsonReader reader, ChanReaderProcessingQueue queue) {}
        };
    }

    @Override
    public SiteActions actions() {
        return new SiteActions() {
            @Override
            public void boards(BoardsListener boardsListener) {}

            @Override
            public void pages(Board board, PagesListener pagesListener) {}

            @Override
            public void post(Loadable loadableWithDraft, PostListener postListener) {}

            @Override
            public boolean postRequiresAuthentication() {
                return false;
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromNone();
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {}

            @Override
            public void archive(Board board, ArchiveListener archiveListener) {}

            @Override
            public void login(LoginRequest loginRequest, LoginListener loginListener) {}

            @Override
            public void logout() {}

            @Override
            public boolean isLoggedIn() {
                return false;
            }

            @Override
            public LoginRequest getLoginDetails() { return new LoginRequest("", ""); }
        };
    }

    @Override
    public Board board(String code) {
        return Board.getDummyBoard();
    }

    @Override
    public Board createBoard(String name, String code) {
        return Board.getDummyBoard();
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return new ChunkDownloaderSiteProperties(false, false);
    }
}
