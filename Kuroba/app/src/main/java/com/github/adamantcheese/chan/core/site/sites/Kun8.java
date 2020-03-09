package com.github.adamantcheese.chan.core.site.sites;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.Reply;

import java.util.Map;

import okhttp3.HttpUrl;

public class Kun8
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private final String[] mediaHosts = new String[]{"media.8kun.top"};

        @Override
        public Class<? extends Site> getSiteClass() {
            return Kun8.class;
        }

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
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setName("8kun");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://8kun.top/static/favicon.ico")));
        setBoardsType(BoardsType.INFINITE);
        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://8kun.top", "https://sys.8kun.top") {
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return HttpUrl.parse(
                        "https://media.8kun.top/" + "file_store/" + (arg.get("tim") + "." + arg.get("ext")));
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

                return HttpUrl.parse(
                        "https://media.8kun.top/" + "file_store/" + "thumb/" + (arg.get("tim") + "." + ext));
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
                return SiteAuthentication.fromUrl(
                        "https://sys.8kun.top/dnsbls_bypass_popup.php?_=" + System.currentTimeMillis(),
                        "You failed the CAPTCHA",
                        "You may now go back and make your post"
                );
            }
        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return chunkDownloaderSiteProperties;
    }
}