package com.github.adamantcheese.chan.core.site.sites.dvach;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.OptionsSetting;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteSetting;
import com.github.adamantcheese.chan.core.site.common.CommonReplyHttpCall;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.DvachCommentParser;
import com.github.adamantcheese.chan.core.site.parser.DvachPostParser;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

public class Dvach extends CommonSite {
    private static final String TAG = "Dvach";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Dvach.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://2ch.hk");
        }

        @Override
        public String[] getNames() {
            return new String[]{"dvach", "2ch"};
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

    static final String CAPTCHA_KEY = "6LeQYz4UAAAAAL8JCk35wHSv6cuEV5PyLhI6IxsM";

    private OptionsSetting<Chan4.CaptchaType> captchaType;

    @Override
    public void initializeSettings() {
        super.initializeSettings();
        captchaType = new OptionsSetting<>(settingsProvider,
                "preference_captcha_type",
                Chan4.CaptchaType.class, Chan4.CaptchaType.V2JS);
    }

    @Override
    public List<SiteSetting> settings() {
        return Collections.singletonList(
                SiteSetting.forOption(
                        captchaType,
                        "Captcha type",
                        Arrays.asList("Javascript", "Noscript"))
        );
    }

    @Override
    public void setParser(CommentParser commentParser) {
        this.postParser = new DvachPostParser(commentParser);
    }

    @Override
    public void setup() {
        setName("2ch.hk");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://2ch.hk/favicon.ico")));
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://2ch.hk",
                "https://2ch.hk") {
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return root.builder().s(arg.get("path")).url();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return root.builder().s(arg.get("thumbnail")).url();
            }

            @Override
            public HttpUrl boards() {
                return new HttpUrl.Builder().scheme("https").host("2ch.hk").addPathSegment("boards.json").build();
            }

            @Override
            public HttpUrl reply(Loadable loadable) {
                return new HttpUrl.Builder().scheme("https").host("2ch.hk").addPathSegment("makaba").addPathSegment("posting.fcgi").addQueryParameter("json", "1").build();
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
            public void post(Reply reply, final PostListener postListener) {
                httpCallManager.makeHttpCall(new DvachReplyCall(Dvach.this, reply), new HttpCall.HttpCallback<CommonReplyHttpCall>() {
                    @Override
                    public void onHttpSuccess(CommonReplyHttpCall httpPost) {
                        postListener.onPostComplete(httpPost, httpPost.replyResponse);
                    }

                    @Override
                    public void onHttpFail(CommonReplyHttpCall httpPost, Exception e) {
                        postListener.onPostError(httpPost, e);
                    }
                }, postListener::onUploadingProgress);
            }

            @Override
            public boolean postRequiresAuthentication() {
                return !isLoggedIn();
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                if (isLoggedIn()) {
                    return SiteAuthentication.fromNone();
                } else {
                    switch (captchaType.get()) {
                        case V2JS:
                            return SiteAuthentication.fromCaptcha2(CAPTCHA_KEY, "https://2ch.hk/api/captcha/recaptcha/mobile");
                        case V2NOJS:
                            return SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY, "https://2ch.hk/api/captcha/recaptcha/mobile");
                        default:
                            throw new IllegalArgumentException();
                    }
                }
            }

            @Override
            public void delete(DeleteRequest deleteRequest, DeleteListener deleteListener) {
                super.delete(deleteRequest, deleteListener);
            }

            @Override
            public void boards(final BoardsListener listener) {
                requestQueue.add(new DvachBoardsRequest(Dvach.this, response -> listener.onBoardsReceived(new Boards(response)), (error) -> {
                    Logger.e(TAG, "Failed to get boards from server", error);

                    // API fail, provide some default boards
                    List<Board> list = new ArrayList<>();
                    list.add(Board.fromSiteNameCode(Dvach.this, "бред", "b"));
                    list.add(Board.fromSiteNameCode(Dvach.this, "Видеоигры, general, официальные треды", "vg"));
                    list.add(Board.fromSiteNameCode(Dvach.this, "новости", "news"));
                    list.add(Board.fromSiteNameCode(Dvach.this, "политика, новости, ольгинцы, хохлы, либерахи, рептилоиды.. oh shi", "po"));
                    Collections.shuffle(list);
                    listener.onBoardsReceived(new Boards(list));
                }));
            }
        });

        setApi(new DvachApi(this));

        setParser(new DvachCommentParser());
    }
}
