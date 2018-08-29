/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.site.sites.chan55;

import android.support.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.vichan.VichanActions;
import org.floens.chan.core.site.common.vichan.VichanApi;
import org.floens.chan.core.site.common.vichan.VichanCommentParser;
import org.floens.chan.core.site.common.vichan.VichanEndpoints;

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
                        .addPathSegment(String.valueOf(loadable.no) + ".html")
                        .toString();
            } else {
                return getUrl().toString();
            }
        }
    };

    @Override
    public void setup() {
        setName("55chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://55chan.org/static/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "Random", "b"),
                Board.fromSiteNameCode(this, "Falha e Aleatoriedade", "mago"),
                Board.fromSiteNameCode(this, "Moderação", "mod"),
                Board.fromSiteNameCode(this, "International", "int"),
                Board.fromSiteNameCode(this, "Assuntos Nipônicos", "an"),
                Board.fromSiteNameCode(this, "Jogos", "jo"),
                Board.fromSiteNameCode(this, "Online Multiplayer", "lan"),
                Board.fromSiteNameCode(this, "Música", "mu"),
                Board.fromSiteNameCode(this, "Cartoons & Comics", "hq"),
                Board.fromSiteNameCode(this, "Televisão", "tv"),
                Board.fromSiteNameCode(this, "Literatura", "lit"),
                Board.fromSiteNameCode(this, "Computaria em geral", "comp"),
                Board.fromSiteNameCode(this, "Criação", "cri"),
                Board.fromSiteNameCode(this, "Idiomas", "lang"),
                Board.fromSiteNameCode(this, "DIY, gambiarras e projetos", "macgyver"),
                Board.fromSiteNameCode(this, "Automóveis", "pfiu"),
                Board.fromSiteNameCode(this, "Culinária", "coz"),
                Board.fromSiteNameCode(this, "Universidade Federal do 55chan", "UF55"),
                Board.fromSiteNameCode(this, "Finanças", "$"),
                Board.fromSiteNameCode(this, "Politicamente Incorreto", "pol"),
                Board.fromSiteNameCode(this, "yt", "yt"),
                Board.fromSiteNameCode(this, "1997", "1997"),
                Board.fromSiteNameCode(this, "Esportes", "esp"),
                Board.fromSiteNameCode(this, "Viadices", "clô"),
                Board.fromSiteNameCode(this, "Buseta e Depressão", "escoria"),
                Board.fromSiteNameCode(this, "Pornografia 2D", "34"),
                Board.fromSiteNameCode(this, "*fapfapfap*", "pr0n"),
                Board.fromSiteNameCode(this, "Pintos Femininos", "tr"),
                Board.fromSiteNameCode(this, "Ai, que delícia, cara", "pinto")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://55chan.org",
                "https://55chan.org"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}
