package com.github.adamantcheese.chan.core.site.sites;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;

import java.util.Map;

import okhttp3.HttpUrl;

public class Kun8
        extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private final String[] mediaHosts = new String[]{"media.8kun.top"};

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://8kun.top/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"8kun"};
        }

        @Override
        public String[] getMediaHosts() {
            return mediaHosts;
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode)
                        .addPathSegment("res")
                        .addPathSegment(loadable.no + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    public Kun8() {
        setName("8kun");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://8kun.top/static/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoardsType(BoardsType.INFINITE);
        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING
                        || siteFeature == SiteFeature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://8kun.top", "https://sys.8kun.top") {

            //8kun changed directory structures after this date (8/25/2016), so we need to switch off that to make it work
            private final long IMAGE_CHANGE_DATE = 1472083200L;

            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                if (post.unixTimestampSeconds > IMAGE_CHANGE_DATE) {
                    return HttpUrl.parse(
                            "https://media.8kun.top/" + "file_store/" + arg.get("tim") + "." + arg.get("ext"));
                } else {
                    return HttpUrl.parse("https://media.8kun.top/" + post.board.code + "/src/" + arg.get("tim") + "."
                            + arg.get("ext"));
                }
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

                if (post.unixTimestampSeconds > IMAGE_CHANGE_DATE) {
                    return HttpUrl.parse(
                            "https://media.8kun.top/" + "file_store/" + "thumb/" + arg.get("tim") + "." + ext);
                } else {
                    // this is imperfect, for some reason some thumbnails are png and others are jpg randomly
                    // kinda mucks up the image viewing as well
                    return HttpUrl.parse(
                            "https://media.8kun.top/" + post.board.code + "/thumb/" + arg.get("tim") + "." + ext);
                }
            }
        });

        setActions(new VichanActions(this) {
            @Override
            public void setupPost(Loadable loadable, MultipartHttpCall<ReplyResponse> call) {
                super.setupPost(loadable, call);

                if (loadable.isThreadMode()) {
                    // "thread" is already added in VichanActions.
                    call.parameter("post", "New Reply");
                } else {
                    call.parameter("post", "New Thread");
                    call.parameter("page", "1");
                }
            }

            @Override
            public void prepare(
                    MultipartHttpCall<ReplyResponse> call,
                    Loadable loadable,
                    NetUtilsClasses.ResponseResult<Void> callback
            ) {
                // don't need to check antispam for this site
                callback.onSuccess(null);
            }

            @Override
            public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
                return SiteAuthentication.fromUrl(
                        "https://sys.8kun.top/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post"
                );
            }
        });

        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}