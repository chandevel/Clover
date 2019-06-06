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
package com.github.adamantcheese.chan.core.site.sites.Wired7;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;

import okhttp3.HttpUrl;

public class Wired7 extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Wired7.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://wired-7.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"Wired-7, wired7, Wired7"};
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
        setName("Wired-7");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://wired-7.org/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "Lewds & +18", "18"),
                Board.fromSiteNameCode(this, "Random", "b"),
                Board.fromSiteNameCode(this, "Hentai", "h"),
                Board.fromSiteNameCode(this, "Humanidad", "hum"),
                Board.fromSiteNameCode(this, "Internacional/Random", "i"),
                Board.fromSiteNameCode(this, "Política", "pol"),
                Board.fromSiteNameCode(this, "Wired-7 Metaboard", "meta"),
                Board.fromSiteNameCode(this, "Anime", "a"),
                Board.fromSiteNameCode(this, "Cultura Japonesa", "jp"),
                Board.fromSiteNameCode(this, "Musica & Audio", "mu"),
                Board.fromSiteNameCode(this, "Tecnología", "tech"),
                Board.fromSiteNameCode(this, "Videojuegos y Gaming", "v"),
                Board.fromSiteNameCode(this, "Medios Visuales", "vis"),
                Board.fromSiteNameCode(this, "Paranormal", "x"),
                Board.fromSiteNameCode(this, "Lain", "lain")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this,
                "https://wired-7.org",
                "https://wired-7.org"));
        setActions(new Wired7Actions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }
}
