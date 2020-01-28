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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.ChunkDownloaderSiteProperties;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanActions;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanApi;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanEndpoints;

import okhttp3.HttpUrl;

public class Sushichan
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://sushigirl.us/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Sushichan.class;
        }

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
            return new String[]{"sushichan"};
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
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(true, true);
    }

    @Override
    public void setup() {
        setName("Sushichan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://sushigirl.us/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "artsy", "wildcard"),
                Board.fromSiteNameCode(this, "sushi social", "lounge"),
                Board.fromSiteNameCode(this, "vidya gaems", "arcade"),
                Board.fromSiteNameCode(this, "cute things", "kawaii"),
                Board.fromSiteNameCode(this, "tasty morsels & delights", "kitchen"),
                Board.fromSiteNameCode(this, "enjoyable sounds", "tunes"),
                Board.fromSiteNameCode(this, "arts & literature", "culture"),
                Board.fromSiteNameCode(this, "technology", "silicon"),
                Board.fromSiteNameCode(this, "site meta-discussion", "yakuza"),
                Board.fromSiteNameCode(this, "internet death cult", "hell"),
                Board.fromSiteNameCode(this, "dat ecchi & hentai goodness", "lewd")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://sushigirl.us/", "https://sushigirl.us/"));
        setActions(new VichanActions(this));
        setApi(new VichanApi(this));
        setParser(new VichanCommentParser());
    }

    @NonNull
    @Override
    public ChunkDownloaderSiteProperties getChunkDownloaderSiteProperties() {
        return chunkDownloaderSiteProperties;
    }
}
