package org.floens.chan.core.site.sites.smuglo;

import android.support.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.common.vichan.VichanActions;
import org.floens.chan.core.site.common.vichan.VichanApi;
import org.floens.chan.core.site.common.vichan.VichanCommentParser;
import org.floens.chan.core.site.common.vichan.VichanEndpoints;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.Reply;

import java.util.Map;

import okhttp3.HttpUrl;

public class Smuglo extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Smuglo.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://smuglo.li/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"Sumglo.li", "The eternal 8bunker"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("smuglo.li");
        setIcon(SiteIcon.fromAssets("icons/smuglo.png"));
        setBoardsType(BoardsType.INFINITE);


        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://smuglo.li/",
                "https://smuglo.li/") {
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return root.builder().s(post.board.code).s("src").s(arg.get("tim") + "." + arg.get("ext")).url();
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

                return root.builder().s(post.board.code).s("thumb").s(arg.get("tim") + "." + ext).url();
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
                return false;
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {
                super.delete(deleteRequest, deleteListener);
            }
        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }
}
