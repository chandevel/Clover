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
package org.floens.chan.core.site.sites.chan420;

import androidx.annotation.Nullable;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteIcon;
import org.floens.chan.core.site.common.CommonSite;
import org.floens.chan.core.site.common.taimaba.TaimabaActions;
import org.floens.chan.core.site.common.taimaba.TaimabaApi;
import org.floens.chan.core.site.common.taimaba.TaimabaCommentParser;
import org.floens.chan.core.site.common.taimaba.TaimabaEndpoints;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

public class Chan420 extends CommonSite {
    private static final String TAG = "420Chan";
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        @Override
        public Class<? extends Site> getSiteClass() {
            return Chan420.class;
        }

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://420chan.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"420chan"};
        }

        @Override
        public String desktopUrl(Loadable loadable, @Nullable Post post) {
            if (loadable.isCatalogMode()) {
                return "https://boards.420chan.org/" + loadable.board.code + "/";
            } else if (loadable.isThreadMode()) {
                String url = "https://boards.420chan.org/" + loadable.board.code + "/thread/" + loadable.no;
                if (post != null) {
                    url += "#" + post.no;
                }
                return url;
            } else {
                throw new IllegalArgumentException();
            }
		}
    };

    @Override
    public void setup() {
        setName("420Chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://420chan.org/favicon.ico")));
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING || feature == Feature.POST_REPORT;
            }
        });

        setEndpoints(new TaimabaEndpoints(this,
                "https://api.420chan.org",
                "https://boards.420chan.org"));
        setActions(new TaimabaActions(this) {
            @Override
            public void boards(final BoardsListener listener) {
                requestQueue.add(new Chan420BoardsRequest(Chan420.this, response -> {
                    listener.onBoardsReceived(new Boards(response));
                }, (error) -> {
                    Logger.e(TAG, "Failed to get boards from server", error);

                    // API fail, provide some default boards
                    List<Board> list = new ArrayList<>();
                    list.add(new Board(Chan420.this, "Cannabis Discussion", "weed", true, true));
                    list.add(new Board(Chan420.this, "Alcohol Discussion", "hooch", true, true));
                    list.add(new Board(Chan420.this, "Dream Discussion", "dr", true, true));
                    list.add(new Board(Chan420.this, "Detoxing & Rehabilitation", "detox", true, true));
                    listener.onBoardsReceived(new Boards(list));
                }));
            }
        });
        setApi(new TaimabaApi(this));
        setParser(new TaimabaCommentParser());
    }
}