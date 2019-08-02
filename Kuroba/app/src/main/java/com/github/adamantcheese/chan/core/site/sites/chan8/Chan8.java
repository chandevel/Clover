package com.github.adamantcheese.chan.core.site.sites.chan8;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Map;

import okhttp3.HttpUrl;

public class Chan8 extends CommonSite {
    private static final String TAG = "Chan8";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan8.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://8ch.net/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"8chan", "8ch"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable final Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(loadable.no + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("8chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://8ch.net/static/favicon.ico")));
        setBoardsType(BoardsType.INFINITE);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://8ch.net",
                "https://sys.8ch.net") {
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return root.builder().s("file_store").s(arg.get("tim") + "." + arg.get("ext")).url();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                String ext;
                switch (arg.get("ext")) {
                    case "jpeg":
                    case "jpg":
                    case "png":
                    case "gif":
                        ext = arg.get("ext");
                        break;
                    default:
                        ext = "jpg";
                        break;
                }

                return root.builder().s("file_store").s("thumb").s(arg.get("tim") + "." + ext).url();
            }

            @Override
            public HttpUrl boards() {
                return root.builder().s("boards.json").url();
            }
        });

        setActions(new VichanActions(this) {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                super.setupPost(reply, call);

                if (reply.loadable.isThreadMode()) {
                    // "thread" is already added in VichanActions.
                    call.parameter("post", "New Reply");
                } else {
                    call.parameter("post", "New Thread");
                    call.parameter("page", "1");
                }
            }

            @Override
            public boolean requirePrepare() {
                // We don't need to check the antispam fields for 8chan.
                return false;
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl("https://8ch.net/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post");
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {
                super.delete(deleteRequest, deleteListener);
            }

            @Override
            public void boards(final BoardsListener listener) {
                requestQueue.add(new Chan8BoardsRequest(Chan8.this, response -> {
                    Boards boards = new Boards(response);
                    //sudo is a hidden board
                    Board sudo = Board.fromSiteNameCode(Chan8.this, "8chan Tech Support", "sudo");
                    boards.boards.add(sudo);
                    listener.onBoardsReceived(boards);
                    boardManager.updateAvailableBoardsForSite(Chan8.this, boards.boards);
                }, (error) -> {
                    Logger.e(TAG, "Failed to get boards from server", error);
                    listener.onBoardsReceived(new Boards(new ArrayList<>()));
                }));
            }
        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }

    @Override
    public BoardsType boardsType() {
        // yes, boards.json
        // TODO for the time being this is disabled until the board manager is fixed to not save all boards
        return BoardsType.INFINITE;
    }
}
