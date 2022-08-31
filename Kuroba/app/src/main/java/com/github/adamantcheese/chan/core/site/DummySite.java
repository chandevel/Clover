package com.github.adamantcheese.chan.core.site;

import static com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.JsonReader;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
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

import java.util.*;

import okhttp3.Call;
import okhttp3.HttpUrl;

/**
 * A dummy site, generally used internally and should not show up as user selectable in the site setup area.
 */
public class DummySite
        implements Site {

    private final HttpUrl DUMMY_ROOT = HttpUrl.get("https://www.example.com");

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
        return SiteIcon.fromDrawable(new BitmapDrawable(getAppContext().getResources(),
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
            public boolean respondsTo(@NonNull HttpUrl url) {
                return DUMMY_ROOT.host().equals(url.host());
            }

            @Override
            public String desktopUrl(Loadable loadable, int postNo) {
                if (loadable.isThreadMode()) {
                    String url = DUMMY_ROOT
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
                    return DUMMY_ROOT.newBuilder().addPathSegment(loadable.boardCode).build().toString();
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
                return DUMMY_ROOT.newBuilder().addPathSegment(board.code).addPathSegment("catalog.json").build();
            }

            @Override
            public HttpUrl thread(Loadable loadable) {
                return DUMMY_ROOT
                        .newBuilder()
                        .addPathSegment(loadable.board.code)
                        .addPathSegment("thread")
                        .addPathSegment(loadable.no + ".json")
                        .build();
            }

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return DUMMY_ROOT.newBuilder().addPathSegment(post.board.code).addPathSegment("test.png").build();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return DUMMY_ROOT.newBuilder().addPathSegment(post.board.code).addPathSegment("test.png").build();
            }

            @Override
            public Pair<HttpUrl, PassthroughBitmapResult> icon(IconType icon, Map<String, String> arg) {
                return new Pair<>(DUMMY_ROOT.newBuilder().addPathSegment("test.png").build(),
                        new PassthroughBitmapResult()
                );
            }

            @Override
            public HttpUrl boards() {
                return DUMMY_ROOT.newBuilder().addPathSegment("boards.json").build();
            }

            @Override
            public HttpUrl pages(Board board) {
                return DUMMY_ROOT.newBuilder().addPathSegment(board.code).addPathSegment("threads.json").build();
            }

            @Override
            public HttpUrl archive(Board board) {
                return DUMMY_ROOT.newBuilder().addPathSegment(board.code).addPathSegment("archive").build();
            }

            @Override
            public HttpUrl reply(Loadable thread) {
                return DUMMY_ROOT.newBuilder().addPathSegment(thread.boardCode).addPathSegment("post").build();
            }

            @Override
            public HttpUrl delete(Post post) {
                return DUMMY_ROOT.newBuilder().addPathSegment(post.board.code).addPathSegment("imgboard.php").build();
            }

            @Override
            public HttpUrl report(Post post) {
                return DUMMY_ROOT
                        .newBuilder()
                        .addPathSegment(post.board.code)
                        .addPathSegment("imgboard.php")
                        .addQueryParameter("mode", "report")
                        .addQueryParameter("no", String.valueOf(post.no))
                        .build();
            }

            @Override
            public HttpUrl login() {
                return DUMMY_ROOT.newBuilder().addPathSegment("auth").build();
            }
        };
    }

    @Override
    public ChanReader chanReader() {
        return new ChanReader() {
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
    public SiteActions actions() {
        return new SiteActions() {
            @Override
            public void boards(ResponseResult<Boards> boardsListener) {}

            @Override
            public void pages(Board board, ResponseResult<ChanPages> pagesListener) {}

            @Override
            public Call post(
                    Loadable loadableWithDraft, PostListener postListener
            ) {return new NetUtilsClasses.NullCall(DUMMY_ROOT);}

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
