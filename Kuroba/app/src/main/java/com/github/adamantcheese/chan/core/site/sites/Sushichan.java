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
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentAction;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanPostParser;

import okhttp3.HttpUrl;

public class Sushichan
        extends CommonSite {
    private static final HttpUrl ROOT = HttpUrl.get("https://sushigirl.us/");
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public HttpUrl getUrl() {
            return ROOT;
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

    public Sushichan() {
        setName("Sushichan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.get("https://sushigirl.us/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoards(
                Board.fromSiteNameCode(this, "artsy", "wildcard"),
                Board.fromSiteNameCode(this, "sushi social", "lounge"),
                Board.fromSiteNameCode(this, "vidya gaems", "arcade"),
                Board.fromSiteNameCode(this, "cute things", "kawaii"),
                Board.fromSiteNameCode(this, "tasty morsels & delights", "kitchen"),
                Board.fromSiteNameCode(this, "enjoyable sounds", "tunes"),
                Board.fromSiteNameCode(this, "arts & literature", "culture"),
                Board.fromSiteNameCode(this, "technology", "silicon"),
                Board.fromSiteNameCode(this, "Japan / Otaku / Anime", "otaku"),
                Board.fromSiteNameCode(this, "site meta-discussion", "yakuza"),
                Board.fromSiteNameCode(this, "internet death cult", "hell"),
                Board.fromSiteNameCode(this, "dat ecchi & hentai goodness", "lewd")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                return super.siteFeature(siteFeature) || siteFeature == SiteFeature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://sushigirl.us/", "https://sushigirl.us/"));
        setActions(new VichanActions(this) {
            @Override
            public void clearCookies() {
                NetUtils.clearAllCookies(ROOT);
            }
        });
        setApi(new VichanApi(this));
        setParser(new VichanPostParser(new VichanCommentAction()));
    }
}
