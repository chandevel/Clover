/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.site.sites;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;

import okhttp3.HttpUrl;

public class Lainchan
        extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://lainchan.org/";

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
            return new String[]{"lainchan"};
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

    public Lainchan() {
        setName("Lainchan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://lainchan.org/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoards(
                Board.fromSiteNameCode(this, "Programming", "λ"),
                Board.fromSiteNameCode(this, "Do It Yourself", "Δ"),
                Board.fromSiteNameCode(this, "Security", "sec"),
                Board.fromSiteNameCode(this, "Technology", "Ω"),
                Board.fromSiteNameCode(this, "Games and Interactive Media", "inter"),
                Board.fromSiteNameCode(this, "Literature", "lit"),
                Board.fromSiteNameCode(this, "Musical and Audible Media", "music"),
                Board.fromSiteNameCode(this, "Visual Media", "vis"),
                Board.fromSiteNameCode(this, "Humanity", "hum"),
                Board.fromSiteNameCode(this, "Drugs 3.0", "drug"),
                Board.fromSiteNameCode(this, "Consciousness and Dreams", "zzz"),
                Board.fromSiteNameCode(this, "layer", "layer"),
                Board.fromSiteNameCode(this, "Questions and Complaints", "q"),
                Board.fromSiteNameCode(this, "Random", "r"),
                Board.fromSiteNameCode(this, "Lain", "lain"),
                Board.fromSiteNameCode(this, "Culture 15 freshly bumped threads", "culture"),
                Board.fromSiteNameCode(this, "Psychopharmacology 15 freshly bumped threads", "psy"),
                Board.fromSiteNameCode(this, "15 freshly bumped threads", "mega")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://lainchan.org", "https://lainchan.org"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}
