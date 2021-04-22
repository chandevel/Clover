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
package com.github.adamantcheese.chan.core.site.sites.chan420;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaActions;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaApi;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaCommentParser;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaEndpoints;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.Collections;

import okhttp3.HttpUrl;

public class Chan420
        extends CommonSite {
    public static final CommonSiteUrlHandler URL_HANDLER = new CommonSiteUrlHandler() {
        private final String[] mediaHosts = new String[]{"boards.420chan.org"};

        @Override
        public HttpUrl getUrl() {
            return HttpUrl.parse("https://420chan.org/");
        }

        @Override
        public String[] getNames() {
            return new String[]{"420chan", "420"};
        }

        @Override
        public String[] getMediaHosts() {
            return mediaHosts;
        }

        @Override
        public String desktopUrl(Loadable loadable, int postNo) {
            if (loadable.isCatalogMode()) {
                return "https://boards.420chan.org/" + loadable.boardCode + "/thread/" + (postNo > 0 ? postNo : "");
            } else if (loadable.isThreadMode()) {
                String url = "https://boards.420chan.org/" + loadable.boardCode + "/thread/" + loadable.no;
                if (postNo > 0 && loadable.no != postNo) {
                    url += "#" + postNo;
                }
                return url;
            } else {
                return "https://boards.420chan.org/" + loadable.boardCode + "/";
            }
        }
    };

    public Chan420() {
        setName("420Chan");
        setIcon(SiteIcon.fromFavicon(HttpUrl.parse("https://420chan.org/favicon.ico")));
    }

    @Override
    public void setup() {
        setBoardsType(BoardsType.DYNAMIC);

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean siteFeature(SiteFeature siteFeature) {
                //420chan doesn't support file hashes
                return (super.siteFeature(siteFeature) && siteFeature != SiteFeature.IMAGE_FILE_HASH)
                        || siteFeature == SiteFeature.POSTING || siteFeature == SiteFeature.POST_REPORT;
            }
        });

        setEndpoints(new TaimabaEndpoints(this, "https://api.420chan.org", "https://boards.420chan.org"));
        setActions(new TaimabaActions(this) {
            @Override
            public void boards(final ResponseResult<Boards> listener) {
                NetUtils.makeJsonRequest(endpoints().boards(), new ResponseResult<Boards>() {
                    @Override
                    public void onFailure(Exception e) {
                        Logger.e(Chan420.this, "Failed to get boards from server", e);
                        Boards list = new Boards();
                        list.add(Board.fromSiteNameCode(Chan420.this, "Cannabis Discussion", "weed"));
                        list.add(Board.fromSiteNameCode(Chan420.this, "Alcohol Discussion", "hooch"));
                        list.add(Board.fromSiteNameCode(Chan420.this, "Dream Discussion", "dr"));
                        list.add(Board.fromSiteNameCode(Chan420.this, "Detoxing & Rehabilitation", "detox"));
                        Collections.shuffle(list);
                        listener.onSuccess(list);
                    }

                    @Override
                    public void onSuccess(Boards result) {
                        listener.onSuccess(result);
                    }
                }, new Chan420BoardsParser(Chan420.this), NetUtilsClasses.ONE_DAY_CACHE);
            }
        });
        setApi(new TaimabaApi(this));
        setParser(new TaimabaCommentParser());
    }
}
