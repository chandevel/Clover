package com.github.adamantcheese.chan.core.site.sites.chan55;

import android.os.Handler;
import android.os.Looper;

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
import com.github.adamantcheese.chan.core.site.http.HttpCall;
import com.github.adamantcheese.chan.core.site.http.ProgressRequestBody;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.http.ReplyResponse;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.Jsoup;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class Chan55 extends CommonSite {
    private static final String TAG = "Chan55";
    private static final String BASE_URL = "https://55chan.org";
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan55.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://55chan.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"55chan"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return String.format("%s/%s", BASE_URL, loadable.boardCode);
            } else if (loadable.isThreadMode()) {
                return String.format("%s/%s/res/%s.html", BASE_URL, loadable.boardCode, loadable.no);
            } else {
                return BASE_URL;
            }
        }
    };

    private static final String errorPatternString = "\"error\":\"";
    private static final Pattern authPattern = Pattern.compile("dose");
    private static final Pattern bannedPattern = Pattern.compile("banned");
    private static final Pattern errorPattern = Pattern.compile(errorPatternString);
    private static final Pattern postPattern = Pattern.compile("\\.html#\\d+");
    private static final Pattern threadPattern = Pattern.compile("\\d+.html");
    private static final Random secureRandom = new SecureRandom();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void setup() {
        setBoardsType(BoardsType.STATIC);
        setName("55chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://55chan.org/static/favicon.ico?v=2")));
        setBoardsType(BoardsType.STATIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_DELETE;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                BASE_URL,
                BASE_URL) {
            @Override
            public HttpUrl imageUrl(Post.Builder post, Map<String, String> arg) {
                return root.builder().s(post.board.code).s("src").s(arg.get("tim") + "." + arg.get("ext")).url();
            }

            @Override
            public HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg) {
                return root.builder().s(post.board.code).s("thumb").s(arg.get("tim") + "." + arg.get("ext")).url();
            }

            @Override
            public HttpUrl reply(Loadable loadable) {
                return sys.builder().s("altpost.php").url();
            }
        });

        setActions(new VichanActions(this) {
            @Override
            public void setupPost(Reply reply, MultipartHttpCall call) {
                super.setupPost(reply, call);

                if (reply.loadable.isThreadMode()) {
                    // "thread" is already added in VichanActions.
                    call.parameter("post", "Responder");
                } else {
                    call.parameter("post", "Nova Thread");
                    call.parameter("page", "1");
                }

                call.parameter("json_response", "1");
            }

            @Override
            public void handlePost(ReplyResponse replyResponse, Response response, String result) {
                Matcher auth = authPattern.matcher(result);
                Matcher banned = bannedPattern.matcher(result);
                Matcher err = errorPattern.matcher(result);
                Matcher postMatcher = postPattern.matcher(result);
                if (auth.find()) {
                    replyResponse.requireAuthentication = true;
                    replyResponse.errorMessage = "";
                } else if (banned.find()) {
                    replyResponse.errorMessage = "Você foi banido";
                } else if (err.find()) {
                    int start = result.indexOf(errorPatternString) + errorPatternString.length();
                    int end = result.indexOf('\"', start);
                    replyResponse.errorMessage = result.substring(start, end);
                } else {
                    Matcher m = threadPattern.matcher(result);
                    try {
                        if (m.find()) {
                            String thread = m.group(0);
                            int dotIndex = thread.indexOf('.');
                            replyResponse.threadNo = Integer.parseInt(thread.substring(0, dotIndex));

                            if (postMatcher.find()) {
                                String post = postMatcher.group(0);
                                int hashIndex = post.indexOf('#');
                                replyResponse.postNo = Integer.parseInt(post.substring(hashIndex + 1));
                            } else {
                                replyResponse.postNo = replyResponse.threadNo;
                            }
                            replyResponse.posted = true;
                        }
                    } catch (Exception ex) {
                        String message = ex.getMessage();
                        Logger.d(TAG, String.format("Error handling the post response : %s", message));
                        replyResponse.errorMessage = String.format("Error posting: %s", message);
                    }
                }
            }

            @Override
            public boolean requirePrepare() {
                return false;
            }

            @Override
            public SiteAuthentication postAuthenticate() {
                return SiteAuthentication.fromUrl("https://55chan.org/dose_diaria.php",
                        "errou o CAPTCHA",
                        "pode retornar");
            }

            @Override
            public void boards(final BoardsListener listener) {
                List<Board> all = new ArrayList<>();

                all.add(Board.fromSiteNameCode(Chan55.this, "Random", "b"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Falha e Aleatoriedade", "mago"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Moderação", "mod"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Internacional", "int"));

                all.add(Board.fromSiteNameCode(Chan55.this, "Assuntos nipônicos", "an"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Sfchan", "sfc"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Jogos", "jo"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Online Multiplayer", "lan"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Música", "mu"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Cartoons & Comics", "hq"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Televisão", "tv"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Literatura", "lit"));

                all.add(Board.fromSiteNameCode(Chan55.this, "Computaria em geral", "comp"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Criação", "cri"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Idiomas", "lang"));
                all.add(Board.fromSiteNameCode(Chan55.this, "DIY, gambiarras e projetos", "macgyver"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Automóveis", "pfiu"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Culinária", "coz"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Universidade Federal do 55chan", "UF55"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Finanças", "$"));

                all.add(Board.fromSiteNameCode(Chan55.this, "Politicamente Incorreto", "pol"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Esportes", "esp"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Viadices", "clô"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Buseta e Depressão", "escoria"));

                all.add(Board.fromSiteNameCode(Chan55.this, "Pornografia 2D", "34"));
                all.add(Board.fromSiteNameCode(Chan55.this, "*fapfapfap*", "pr0n"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Pintos Femininos", "tr"));
                all.add(Board.fromSiteNameCode(Chan55.this, "Ai, que delícia, cara", "pinto"));

                listener.onBoardsReceived(new Boards(all));
                boardManager.updateAvailableBoardsForSite(Chan55.this, all);
            }

            @Override
            public void post(Reply reply, PostListener postListener) {
                ReplyResponse replyResponse = new ReplyResponse();

                reply.password = Long.toHexString(secureRandom.nextLong());
                replyResponse.password = reply.password;

                MultipartHttpCall call = new MultipartHttpCall(site) {
                    @Override
                    public void process(Response response, String result) {
                        handlePost(replyResponse, response, result);
                    }

                    @Override
                    public void setup(
                            Request.Builder requestBuilder,
                            @Nullable ProgressRequestBody.ProgressRequestListener progressListener
                    ) {
                        super.setup(requestBuilder, progressListener);

                        // Change referer
                        requestBuilder.removeHeader("Referer");
                        requestBuilder.addHeader("Referer", String.format("%s/%s/res/%s.html", BASE_URL, reply.loadable.boardCode, String.valueOf(reply.loadable.no)));
                    }
                };

                call.url(site.endpoints().reply(reply.loadable));

                if (requirePrepare()) {
                    new Thread(() -> {
                        prepare(call, reply, replyResponse);
                        handler.post(() -> {
                            setupPost(reply, call);
                            makePostCall(call, replyResponse, postListener);
                        });
                    }).start();
                } else {
                    setupPost(reply, call);
                    makePostCall(call, replyResponse, postListener);
                }
            }

            private void makePostCall(HttpCall call, ReplyResponse replyResponse, PostListener postListener) {
                httpCallManager.makeHttpCall(call, new HttpCall.HttpCallback<HttpCall>() {
                    @Override
                    public void onHttpSuccess(HttpCall httpCall) {
                        postListener.onPostComplete(httpCall, replyResponse);
                    }

                    @Override
                    public void onHttpFail(HttpCall httpCall, Exception e) {
                        postListener.onPostError(httpCall, e);
                    }
                });
            }
        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }
}
