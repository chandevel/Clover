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
package org.floens.chan.core.site.sites.lolifox;

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

public class Lolifox extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Lolifox.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://lolifox.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"lolifox"};
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
        setName("Lolifox");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://lolifox.org/static/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "Аниме", "a"),
                Board.fromSiteNameCode(this, "Аниме арт", "aa"),
                Board.fromSiteNameCode(this, "Random", "b"),
                Board.fromSiteNameCode(this, "International", "int"),
                Board.fromSiteNameCode(this, "My Little Pony", "mlp"),
                Board.fromSiteNameCode(this, "Работа сайта", "mod"),
                Board.fromSiteNameCode(this, "Risovach", "r"),
                Board.fromSiteNameCode(this, "Refuge", "rf"),
                Board.fromSiteNameCode(this, "Official threads", "rus"),
                Board.fromSiteNameCode(this, "Crypto-Anarchism", "ca"),
                Board.fromSiteNameCode(this, "Электроника", "e"),
                Board.fromSiteNameCode(this, "Programming", "pr"),
                Board.fromSiteNameCode(this, "Education", "ruedu"),
                Board.fromSiteNameCode(this, "Software", "s"),
                Board.fromSiteNameCode(this, "Технач", "tech"),
                Board.fromSiteNameCode(this, "Новости", "news"),
                Board.fromSiteNameCode(this, "pol - Russian Edition", "polru"),
                Board.fromSiteNameCode(this, "Алкоголь", "alco"),
                Board.fromSiteNameCode(this, "Art", "art"),
                Board.fromSiteNameCode(this, "Автомобили", "au"),
                Board.fromSiteNameCode(this, "Фурри", "f"),
                Board.fromSiteNameCode(this, "Иностранные языки", "fl"),
                Board.fromSiteNameCode(this, "Фэнтези", "fs"),
                Board.fromSiteNameCode(this, "Корейская поп-музыка", "kpop"),
                Board.fromSiteNameCode(this, "Магия", "mg"),
                Board.fromSiteNameCode(this, "Музач", "mu"),
                Board.fromSiteNameCode(this, "Touhou", "to"),
                Board.fromSiteNameCode(this, "Тульпа", "tulpa"),
                Board.fromSiteNameCode(this, "Gamedev", "gd"),
                Board.fromSiteNameCode(this, "Massive multiplayer online games", "mmo"),
                Board.fromSiteNameCode(this, "Video games", "vg"),
                Board.fromSiteNameCode(this, "Порно", "fap")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://lolifox.org/",
                "https://lolifox.org/"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}