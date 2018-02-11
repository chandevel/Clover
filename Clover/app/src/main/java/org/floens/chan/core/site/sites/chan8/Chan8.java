package org.floens.chan.core.site.sites.chan8;

import android.support.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.MultipartHttpCall;
import org.floens.chan.core.site.common.vichan.VichanApi;
import org.floens.chan.core.site.common.vichan.VichanEndpoints;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.core.site.http.ReplyResponse;
import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.core.site.parser.StyleRule;
import org.jsoup.Jsoup;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Response;

import static android.text.TextUtils.isEmpty;

public class Chan8 extends CommonSite {
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
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return getUrl().newBuilder().addPathSegment(loadable.boardCode).toString();
            } else if (loadable.isThreadMode()) {
                return getUrl().newBuilder()
                        .addPathSegment(loadable.boardCode).addPathSegment("res")
                        .addPathSegment(String.valueOf(loadable.no) + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("8chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://8ch.net/favicon.ico")));
        setBoardsType(BoardsType.INFINITE);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
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
        });

        setActions(new CommonActions() {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                call.parameter("board", reply.loadable.board.code);

                if (reply.loadable.isThreadMode()) {
                    call.parameter("post", "New Reply");
                    call.parameter("thread", String.valueOf(reply.loadable.no));
                } else {
                    call.parameter("post", "New Thread");
                    call.parameter("page", "1");
                }

                call.parameter("pwd", reply.password);
                call.parameter("name", reply.name);
                call.parameter("email", reply.options);

                if (!reply.loadable.isThreadMode() && !isEmpty(reply.subject)) {
                    call.parameter("subject", reply.subject);
                }

                call.parameter("body", reply.comment);

                if (reply.file != null) {
                    call.fileParameter("file", reply.fileName, reply.file);
                }

                if (reply.spoilerImage) {
                    call.parameter("spoiler", "on");
                }
            }

            @Override
            public void handlePost(ReplyResponse replyResponse, Response response, String result) {
                Matcher auth = Pattern.compile(".*\"captcha\": ?true.*").matcher(result);
                Matcher err = Pattern.compile(".*<h1>Error</h1>.*<h2[^>]*>(.*?)</h2>.*").matcher(result);
                if (auth.find()) {
                    replyResponse.requireAuthentication = true;
                    replyResponse.errorMessage = result;
                } else if (err.find()) {
                    replyResponse.errorMessage = Jsoup.parse(err.group(1)).body().text();
                } else {
                    HttpUrl url = response.request().url();
                    Matcher m = Pattern.compile("/\\w+/\\w+/(\\d+).html").matcher(url.encodedPath());
                    try {
                        if (m.find()) {
                            replyResponse.threadNo = Integer.parseInt(m.group(1));
                            replyResponse.postNo = Integer.parseInt(url.encodedFragment());
                            replyResponse.posted = true;
                        }
                    } catch (NumberFormatException ignored) {
                        replyResponse.errorMessage = "Error posting: could not find posted thread.";
                    }
                }
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl("https://8ch.net/dnsbls_bypass.php",
                        "You failed the CAPTCHA",
                        "You may now go back and make your post");
            }
        });

        setApi(new VichanApi(this));

        CommentParser commentParser = new CommentParser();
        commentParser.addDefaultRules();
        commentParser.setQuotePattern(Pattern.compile(".*#(\\d+)"));
        commentParser.setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)"));
        commentParser.rule(StyleRule.tagRule("p").cssClass("quote").color(StyleRule.Color.INLINE_QUOTE).linkify());

        setParser(commentParser);
    }
}
