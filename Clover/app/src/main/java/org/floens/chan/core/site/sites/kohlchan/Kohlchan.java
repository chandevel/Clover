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
package org.floens.chan.core.site.sites.kohlchan;

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

import okhttp3.HttpUrl;

public class Kohlchan extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Kohlchan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https:///kohlchan.net/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"kohlchan"};
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
        setName("Kohlchan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://kohlchan.net/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "random", "b"),
                Board.fromSiteNameCode(this, "int", "pol"),
                Board.fromSiteNameCode(this, "int", "int"),
                Board.fromSiteNameCode(this, "int", "a"),
                Board.fromSiteNameCode(this, "int", "alt"),
                Board.fromSiteNameCode(this, "int", "bus"),
                Board.fromSiteNameCode(this, "int", "c"),
                Board.fromSiteNameCode(this, "int", "co"),
                Board.fromSiteNameCode(this, "int", "d"),
                Board.fromSiteNameCode(this, "int", "danish"),
                Board.fromSiteNameCode(this, "int", "e"),
                Board.fromSiteNameCode(this, "int", "f"),
                Board.fromSiteNameCode(this, "int", "fb"),
                Board.fromSiteNameCode(this, "int", "fit"),
                Board.fromSiteNameCode(this, "int", "foto"),
                Board.fromSiteNameCode(this, "int", "l"),
                Board.fromSiteNameCode(this, "int", "mali"),
                Board.fromSiteNameCode(this, "int", "med"),
                Board.fromSiteNameCode(this, "int", "mu"),
                Board.fromSiteNameCode(this, "int", "n"),
                Board.fromSiteNameCode(this, "int", "ng"),
                Board.fromSiteNameCode(this, "int", "ph"),
                Board.fromSiteNameCode(this, "int", "prog"),
                Board.fromSiteNameCode(this, "int", "s"),
                Board.fromSiteNameCode(this, "int", "soz"),
                Board.fromSiteNameCode(this, "int", "trv"),
                Board.fromSiteNameCode(this, "int", "tu"),
                Board.fromSiteNameCode(this, "int", "tv"),
                Board.fromSiteNameCode(this, "int", "v"),
                Board.fromSiteNameCode(this, "int", "w"),
                Board.fromSiteNameCode(this, "int", "we"),
                Board.fromSiteNameCode(this, "int", "x"),
                Board.fromSiteNameCode(this, "int", "kohl"),
                Board.fromSiteNameCode(this, "int", "km"),
                Board.fromSiteNameCode(this, "int", "m"),
                Board.fromSiteNameCode(this, "int", "ru"),
                Board.fromSiteNameCode(this, "int", "fefe")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new KohlchanEndpoints(this,
                "https://kohlchan.net",
                "https://kohlchan.net"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}
