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
import com.github.adamantcheese.chan.core.site.common.DefaultPostParser;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.LoginRequest;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;
import com.github.adamantcheese.chan.core.site.parser.ChanReaderProcessingQueue;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.PostParser;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.Request;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class DummySite
        implements Site {

    @Override
    public void initialize(int id, JsonSettings userSettings) {}

    @Override
    public void postInitialize() {}

    @Override
    public int id() {
        return -1;
    }

    @Override
    public String name() {
        return "Dummy";
    }

    @Override
    public SiteIcon icon() {
        return SiteIcon.fromDrawable(new BitmapDrawable(
                getAppContext().getResources(),
                BitmapFactory.decodeResource(getAppContext().getResources(), R.drawable.trash_icon)
        ));
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
            private final HttpUrl dummyUrl = HttpUrl.get("https://www.example.com");

            @Override
            public HttpUrl catalog(Board board) {
                return dummyUrl;
            }

            @Override
            public HttpUrl thread(Loadable loadable) {
                return dummyUrl;
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return dummyUrl;
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return dummyUrl;
            }

            @Override
            public HttpUrl icon(String icon, Map<String, String> arg) {
                return dummyUrl;
            }

            @Override
            public HttpUrl boards() {
                return dummyUrl;
            }

            @Override
            public HttpUrl pages(Board board) {
                return dummyUrl;
            }

            @Override
            public HttpUrl archive(Board board) {
                return dummyUrl;
            }

            @Override
            public HttpUrl reply(Loadable thread) {
                return dummyUrl;
            }

            @Override
            public HttpUrl delete(Post post) {
                return dummyUrl;
            }

            @Override
            public HttpUrl report(Post post) {
                return dummyUrl;
            }

            @Override
            public HttpUrl login() {
                return dummyUrl;
            }

            @Override
            public HttpUrl banned() {
                return dummyUrl;
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
            private final PostParser postParser = new DefaultPostParser(new CommentParser().addDefaultRules());

            @Override
            public PostParser getParser() {
                return (theme, builder, filters, callback) -> postParser.parse(theme, builder, filters, callback);
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
        return new ChunkDownloaderSiteProperties(Integer.MAX_VALUE, false, false);
    }
}
