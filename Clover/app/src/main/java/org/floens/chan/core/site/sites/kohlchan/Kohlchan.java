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
                Board.fromSiteNameCode(this, "b", "b"),
                Board.fromSiteNameCode(this, "pol", "pol"),
                Board.fromSiteNameCode(this, "int", "int"),
                Board.fromSiteNameCode(this, "a", "a"),
                Board.fromSiteNameCode(this, "alt", "alt"),
                Board.fromSiteNameCode(this, "bus", "bus"),
                Board.fromSiteNameCode(this, "c", "c"),
                Board.fromSiteNameCode(this, "co", "co"),
                Board.fromSiteNameCode(this, "d", "d"),
                Board.fromSiteNameCode(this, "danish", "danish"),
                Board.fromSiteNameCode(this, "e", "e"),
                Board.fromSiteNameCode(this, "f", "f"),
                Board.fromSiteNameCode(this, "fb", "fb"),
                Board.fromSiteNameCode(this, "fit", "fit"),
                Board.fromSiteNameCode(this, "foto", "foto"),
                Board.fromSiteNameCode(this, "l", "l"),
                Board.fromSiteNameCode(this, "mali", "mali"),
                Board.fromSiteNameCode(this, "med", "med"),
                Board.fromSiteNameCode(this, "mu", "mu"),
                Board.fromSiteNameCode(this, "n", "n"),
                Board.fromSiteNameCode(this, "ng", "ng"),
                Board.fromSiteNameCode(this, "ph", "ph"),
                Board.fromSiteNameCode(this, "prog", "prog"),
                Board.fromSiteNameCode(this, "s", "s"),
                Board.fromSiteNameCode(this, "soz", "soz"),
                Board.fromSiteNameCode(this, "trv", "trv"),
                Board.fromSiteNameCode(this, "tu", "tu"),
                Board.fromSiteNameCode(this, "tv", "tv"),
                Board.fromSiteNameCode(this, "v", "v"),
                Board.fromSiteNameCode(this, "w", "w"),
                Board.fromSiteNameCode(this, "we", "we"),
                Board.fromSiteNameCode(this, "x", "x"),
                Board.fromSiteNameCode(this, "kohl", "kohl"),
                Board.fromSiteNameCode(this, "km", "km"),
                Board.fromSiteNameCode(this, "m", "m"),
                Board.fromSiteNameCode(this, "ru", "ru"),
                Board.fromSiteNameCode(this, "fefe", "fefe")
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
