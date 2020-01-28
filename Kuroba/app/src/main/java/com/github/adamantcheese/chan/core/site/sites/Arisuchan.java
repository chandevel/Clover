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

public class Arisuchan
        extends CommonSite {
    private final ChunkDownloaderSiteProperties chunkDownloaderSiteProperties;

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private static final String ROOT = "https://arisuchan.jp/";

        @Override
        public Class<? extends Site> getSiteClass() {
            return Arisuchan.class;
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
            return new String[]{"arisuchan"};
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

    public Arisuchan() {
        chunkDownloaderSiteProperties = new ChunkDownloaderSiteProperties(
                // The site is dead so we can't know it
                false,
                // The site is dead so we can't know it
                false
        );
    }

    @Override
    public void setup() {
        setName("Arisuchan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://arisuchan.jp/favicon.ico")));

        setBoards(
                Board.fromSiteNameCode(this, "technology", "tech"),
                Board.fromSiteNameCode(this, "art and creative", "art"),
                Board.fromSiteNameCode(this, "culture and media", "cult"),
                Board.fromSiteNameCode(this, "psychology and psychonautics", "psy"),
                Board.fromSiteNameCode(this, "programming", "λ"),
                Board.fromSiteNameCode(this, "shape your world", "Δ"),
                Board.fromSiteNameCode(this, "Киберпанк", "ru"),
                Board.fromSiteNameCode(this, "miscellaneous", "r"),
                Board.fromSiteNameCode(this, "arisuchan meta", "q")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new VichanEndpoints(this, "https://arisuchan.jp", "https://arisuchan.jp"));
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
