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
package org.floens.chan.core.site.sites.geechan;

import androidx.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.tinyib.TinyIBActions;
import org.floens.chan.core.site.common.tinyib.TinyIBApi;
import org.floens.chan.core.site.common.tinyib.TinyIBCommentParser;
import org.floens.chan.core.site.common.tinyib.TinyIBEndpoints;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class Geechan extends CommonSite {
    private static final String TAG = "Geechan";

    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Geechan.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("http://www.geechan.tk");
        }

        @Override
        public String[] getNames() {
            return new String[]{"geechan"};
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
        setName("geechan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("http://www.geechan.tk/favicon.ico")));
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new TinyIBEndpoints(this,
                "http://www.geechan.tk",
                "http://www.geechan.tk") {

            @Override
            public HttpUrl boards() {
                return new HttpUrl.Builder().scheme("http").host("www.geechan.tk").addPathSegment("boards.json").build();
            }
        });

        setActions(new TinyIBActions(this) {
            @Override
            public void boards(final BoardsListener listener) {
                requestQueue.add(new ChanGeeBoardsRequest(Geechan.this, response -> {
                    listener.onBoardsReceived(new Boards(response));
                }, (error) -> {
                    Logger.e(TAG, "Failed to get boards from server", error);

                    // API fail, provide some default boards
                    List<Board> list = new ArrayList<>();
                    list.add(Board.fromSiteNameCode(Geechan.this, "Anime", "a"));
                    list.add(Board.fromSiteNameCode(Geechan.this, "Random", "b"));
                    list.add(Board.fromSiteNameCode(Geechan.this, "Technology", "g"));
                    list.add(Board.fromSiteNameCode(Geechan.this, "World of Warcraft", "wow"));
                    listener.onBoardsReceived(new Boards(list));
                }));
            }
        });

        setApi(new TinyIBApi(this));
        setParser(new TinyIBCommentParser());
    }
}
