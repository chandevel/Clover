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
package com.github.adamantcheese.chan.core.site;

import com.github.adamantcheese.chan.core.model.PostImage;

import java.util.ArrayList;
import java.util.List;

public abstract class ImageSearch {
    public static final List<ImageSearch> engines = new ArrayList<>();

    public abstract String getName();

    public abstract String getUrl(PostImage image);

    static {
        engines.add(new ImageSearch() {
            public String getName() {
                return "Google";
            }

            public String getUrl(PostImage image) {
                return "https://www.google.com/searchbyimage?image_url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "iqdb";
            }

            public String getUrl(PostImage image) {
                return "http://iqdb.org/?url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "SauceNao";
            }

            public String getUrl(PostImage image) {
                return "https://saucenao.com/search.php?url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "TinEye";
            }

            public String getUrl(PostImage image) {
                return "http://tineye.com/search/?url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "trace.moe";
            }

            public String getUrl(PostImage image) {
                return "https://trace.moe/?url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "Pixiv";
            }

            public String getUrl(PostImage image) {
                return "https://www.pixiv.net/member_illust.php?mode=medium&illust_id=" + image.filename.substring(0,
                        image.filename.lastIndexOf('_')
                );
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "Yandex";
            }

            public String getUrl(PostImage image) {
                return "https://yandex.com/images/search?rpt=imageview&url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "Bing";
            }

            public String getUrl(PostImage image) {
                return "https://www.bing.com/images/search?view=detailv2&iss=sbi&form=SBIIRP&sbisrc=UrlPaste&q=imgurl:"
                        + image.getSearchUrl() + "&idpbck=1&selectedindex=0&id=" + image.getSearchUrl()
                        + "&ccid=EgN4f83z&mediaurl=" + image.getSearchUrl() + "&exph=1080&expw=1920&vt=2&sim=11";
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "Derpibooru";
            }

            public String getUrl(PostImage image) {
                return "https://derpibooru.org/search/reverse?url=" + image.getSearchUrl();
            }
        });

        engines.add(new ImageSearch() {
            public String getName() {
                return "Furbooru";
            }

            public String getUrl(PostImage image) {
                return "https://furbooru.org/search/reverse?url=" + image.getSearchUrl();
            }
        });
    }
}
