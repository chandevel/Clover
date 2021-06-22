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
package org.floens.chan.core.site.sites.arisuchan;

import androidx.annotation.Nullable;

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

public class Arisuchan extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Arisuchan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://archive.arisuchan.jp/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"arisuchan"};
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
        setName("Arisuchan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://archive.arisuchan.jp/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "art and design", "art"),
                Board.fromSiteNameCode(this, "culture and media", "cult"),
                Board.fromSiteNameCode(this, "cyberpunk and cybersecurity", "cyb"),
                Board.fromSiteNameCode(this, "personal experiences", "feels"),
                Board.fromSiteNameCode(this, "psychology and psychonautics", "psy"),
                Board.fromSiteNameCode(this, "arisuchan meta", "q"),
                Board.fromSiteNameCode(this, "miscellaneous", "r"),
                Board.fromSiteNameCode(this, "киберпанк-доска", "ru"),
                Board.fromSiteNameCode(this, "science and technology", "tech"),
                Board.fromSiteNameCode(this, "paranoia", "x"),
                Board.fromSiteNameCode(this, "zaibatsu", "z"),
                Board.fromSiteNameCode(this, "diy and projects", "Δ"),
                Board.fromSiteNameCode(this, "programming", "λ")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://archive.arisuchan.jp",
                "https://archive.arisuchan.jp"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}
