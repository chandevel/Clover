package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.settings.primitives.OptionsSetting;
import com.github.adamantcheese.chan.core.site.SiteAuthentication;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.SiteSetting;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.CaptchaType;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.MultipartHttpCall;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.core.site.SiteSetting.Type.OPTIONS;
import static com.github.adamantcheese.chan.core.site.common.CommonDataStructs.CaptchaType.V2JS;

public class Dvach
        extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://2ch.hk";

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse(ROOT);
        }

        @Override
        public String[] getMediaHosts() {
            return new String[]{ROOT};
        }

        @Override
        public String[] getNames() {
            return new String[]{"dvach", "2ch"};
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
    static final String CAPTCHA_KEY = "6LeQYz4UAAAAAL8JCk35wHSv6cuEV5PyLhI6IxsM";
    private OptionsSetting<CaptchaType> captchaType;

    @Override
    public void initializeSettings() {
        super.initializeSettings();
        captchaType = new OptionsSetting<>(settingsProvider, "preference_captcha_type_dvach", CaptchaType.class, V2JS);
    }

    @Override
    public List<SiteSetting<?>> settings() {
        List<SiteSetting<?>> settings = new ArrayList<>();
        SiteSetting<?> captchaSetting =
                new SiteSetting<>("Captcha type", OPTIONS, captchaType, Arrays.asList("Javascript", "Noscript", null));
        settings.add(captchaSetting);
        return settings;
    }

    @Override
    public void setParser(CommentParser commentParser) {
        this.postParser = new DvachPostParser(commentParser);
    }

    public Dvach() {
        setName("2ch.hk");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://2ch.hk/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://2ch.hk", "https://2ch.hk") {
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
                return new HttpUrl.Builder().scheme("https")
                        .host("2ch.hk")
                        .addPathSegment("makaba")
                        .addPathSegment("posting.fcgi")
                        .addQueryParameter("json", "1")
                        .build();
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
                    MultipartHttpCall<ReplyResponse> call, Loadable loadable, ResponseResult<Void> callback
            ) {
                // don't need to check antispam for this site
                callback.onSuccess(null);
            }

            @Override
            public void post(Loadable loadableWithDraft, final PostListener postListener) {
                NetUtils.makeHttpCall(new DvachReplyCall(new NetUtilsClasses.MainThreadResponseResult<>(postListener),
                        loadableWithDraft
                ), postListener);
            }

            @Override
            public boolean postRequiresAuthentication() {
                return true;
            }

            @Override
            public SiteAuthentication postAuthenticate(Loadable loadableWithDraft) {
                switch (captchaType.get()) {
                    case V2JS:
                        return SiteAuthentication.fromCaptcha2(CAPTCHA_KEY,
                                "https://2ch.hk/api/captcha/recaptcha/mobile"
                        );
                    case V2NOJS:
                        return SiteAuthentication.fromCaptcha2nojs(CAPTCHA_KEY,
                                "https://2ch.hk/api/captcha/recaptcha/mobile"
                        );
                    default:
                        throw new IllegalArgumentException();
                }
            }

            @Override
            public void boards(final ResponseResult<Boards> listener) {
                NetUtils.makeJsonRequest(endpoints().boards(), new ResponseResult<Boards>() {
                    @Override
                    public void onFailure(Exception e) {
                        Logger.e(Dvach.this, "Failed to get boards from server", e);

                        // API fail, provide some default boards
                        Boards list = new Boards();
                        list.add(Board.fromSiteNameCode(Dvach.this, "бред", "b"));
                        list.add(Board.fromSiteNameCode(Dvach.this, "Видеоигры, general, официальные треды", "vg"));
                        list.add(Board.fromSiteNameCode(Dvach.this, "новости", "news"));
                        list.add(Board.fromSiteNameCode(Dvach.this,
                                "политика, новости, ольгинцы, хохлы, либерахи, рептилоиды.. oh shi",
                                "po"
                        ));
                        Collections.shuffle(list);
                        listener.onSuccess(list);
                    }

                    @Override
                    public void onSuccess(Boards result) {
                        listener.onSuccess(result);
                    }
                }, new DvachBoardsParser(Dvach.this), NetUtilsClasses.ONE_DAY_CACHE);
            }
        });

        setApi(new DvachApi(this));
        setParser(new DvachCommentParser());
    }
}
