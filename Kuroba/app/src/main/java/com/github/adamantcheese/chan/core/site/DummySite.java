package com.github.adamantcheese.chan.core.site;

import static com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;

import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.InternalSiteArchive;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPages;
import com.github.adamantcheese.chan.core.site.http.*;
import com.github.adamantcheese.chan.core.site.parser.*;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;
import com.github.adamantcheese.chan.utils.BuildConfigUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.*;

/**
 * A dummy site, generally used internally and should not show up as user selectable in the site setup area.
 */
public class DummySite
        implements Site {

    // so that some views properly render in the IDE
    private HttpUrl getDummyRoot() {
        return HttpUrl.get("https://www.example.com");
    }

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
        return SiteIcon.fromFavicon(BuildConfigUtils.TEST_POST_ICON_URL);
    }

    @Override
    public BoardsType boardsType() {
        return BoardsType.STATIC;
    }

    @Override
    public SiteUrlHandler resolvable() {
        return new SiteUrlHandler() {
            @Override
            public boolean respondsTo(@NonNull HttpUrl url) {
                return getDummyRoot().host().equals(url.host());
            }

            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                if (loadable.isThreadMode()) {
                    String url = getDummyRoot()
                            .newBuilder()
                            .addPathSegment(loadable.boardCode)
                            .addPathSegment("thread")
                            .addPathSegment(String.valueOf(loadable.no))
                            .build()
                            .toString();
                    if (postNo > 0 && loadable.no != postNo) {
                        url += "#p" + postNo;
                    }
                    return url;
                } else {
                    return getDummyRoot().newBuilder().addPathSegment(loadable.boardCode).build().toString();
                }
            }

            @Override
            public Loadable resolveLoadable(Site site, HttpUrl url) {
                return Loadable.dummyLoadable();
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
                return getDummyRoot().newBuilder().addPathSegment(board.code).addPathSegment("catalog.json").build();
            }

            @Override
            public HttpUrl thread(Loadable loadable) {
                return getDummyRoot()
                        .newBuilder()
                        .addPathSegment(loadable.board.code)
                        .addPathSegment("thread")
                        .addPathSegment(loadable.no + ".json")
                        .build();
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return getDummyRoot().newBuilder().addPathSegment(post.board.code).addPathSegment("test.png").build();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return getDummyRoot().newBuilder().addPathSegment(post.board.code).addPathSegment("test.png").build();
            }

            @Override
            public Pair<HttpUrl, PassthroughBitmapResult> icon(IconType icon, Map<String, String> arg) {
                return new Pair<>(getDummyRoot().newBuilder().addPathSegment("test.png").build(),
                        new PassthroughBitmapResult()
                );
            }

            @Override
            public HttpUrl boards() {
                return getDummyRoot().newBuilder().addPathSegment("boards.json").build();
            }

            @Override
            public HttpUrl pages(Board board) {
                return getDummyRoot().newBuilder().addPathSegment(board.code).addPathSegment("threads.json").build();
            }

            @Override
            public HttpUrl archive(Board board) {
                return getDummyRoot().newBuilder().addPathSegment(board.code).addPathSegment("archive").build();
            }

            @Override
            public HttpUrl reply(Loadable thread) {
                return getDummyRoot().newBuilder().addPathSegment(thread.boardCode).addPathSegment("post").build();
            }

            @Override
            public HttpUrl delete(Post post) {
                return getDummyRoot()
                        .newBuilder()
                        .addPathSegment(post.board.code)
                        .addPathSegment("imgboard.php")
                        .build();
            }

            @Override
            public HttpUrl report(Post post) {
                return getDummyRoot()
                        .newBuilder()
                        .addPathSegment(post.board.code)
                        .addPathSegment("imgboard.php")
                        .addQueryParameter("mode", "report")
                        .addQueryParameter("no", String.valueOf(post.no))
                        .build();
            }

            @Override
            public HttpUrl login() {
                return getDummyRoot().newBuilder().addPathSegment("auth").build();
            }
        };
    }

    @Override
    public SiteContentReader chanReader() {
        return new SiteContentReader() {
            private final PostParser postParser = new PostParser(new ChanCommentAction());

            @Override
            public PostParser getParser() {
                return postParser;
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
    public SiteApi api() {
        return new SiteApi() {
            @Override
            public void boards(ResponseResult<Boards> boardsListener) {}

            @Override
            public void pages(Board board, ResponseResult<ChanPages> pagesListener) {}

            @Override
            public AtomicReference<Call> post(
                    Loadable loadableWithDraft, PostListener postListener
            ) {return new AtomicReference<>(new NetUtilsClasses.NullCall(getDummyRoot()));}

            @Override
            public boolean postRequiresAuthentication(Loadable loadableWithDraft) {return false;}

            @Override
            public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
                return SiteAuthentication.fromNone();
            }

            @Override
            public void delete(DeleteRequest deleteRequest, ResponseResult<DeleteResponse> deleteListener) {}

            @Override
            public void archive(Board board, ResponseResult<InternalSiteArchive> archiveListener) {}

            @Override
            public void login(String username, String password, ResponseResult<LoginResponse> loginListener) {}

            @Override
            public void logout(final ResponseResult<LoginResponse> loginListener) {}

            @Override
            public boolean isLoggedIn() {return false;}

            @Override
            public LoginRequest getLoginDetails() {return new LoginRequest(DummySite.this, "", "", true);}

            @Override
            public List<Cookie> getCookies() {return Collections.emptyList();}

            @Override
            public void clearCookies() {}
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
}
