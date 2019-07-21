package com.github.adamantcheese.chan.core.site.sites.chan55;

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
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

public class Chan55 extends CommonSite {
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
    private static final String TAG = "Chan55";

    @Override
    public void setup() {
        setName("55chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://55chan.org/static/favicon.ico?v=2")));
        setBoardsType(BoardsType.STATIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                //return feature == Feature.POSTING || feature == Feature.POST_DELETE;
                return false; // posting no supported yet
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://55chan.org",
                "https://55chan.org") {
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

            @Override
            public HttpUrl boards() {
                return root.builder().s("boards.json").url();
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
        });

        setApi(new VichanApi(this));

        setParser(new VichanCommentParser());
    }

    @Override
    public BoardsType boardsType() {
        return BoardsType.STATIC;
    }
}
