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

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteIcon;
import com.github.adamantcheese.chan.core.site.common.CommonSite;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaActions;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaApi;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaCommentParser;
import com.github.adamantcheese.chan.core.site.common.taimaba.TaimabaEndpoints;

import okhttp3.HttpUrl;

public class Chan420 extends CommonSite {
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

        setBoards(
                Board.fromSiteNameCode(this, "Cannabis Discussion", "weed"),
                Board.fromSiteNameCode(this, "Alcohol Discussion", "hooch"),
                Board.fromSiteNameCode(this, "Ecstasy Discussion", "mdma"),
                Board.fromSiteNameCode(this, "Psychadelic Discussion", "psy"),
                Board.fromSiteNameCode(this, "Stimulant Discussion", "stim"),
                Board.fromSiteNameCode(this, "Dissociative Discussion", "dis"),
                Board.fromSiteNameCode(this, "Opiate Discussion", "opi"),
                Board.fromSiteNameCode(this, "Vaping Discussion", "vape"),
                Board.fromSiteNameCode(this, "Tobacco Discussion", "tobacco"),
                Board.fromSiteNameCode(this, "Benzodiazepine Discussion", "benz"),
                Board.fromSiteNameCode(this, "Deliriant Discussion", "deli"),
                Board.fromSiteNameCode(this, "Other Drugs Discussion", "other"),
                Board.fromSiteNameCode(this, "HUFF JENK ERRYDAY", "jenk"),
                Board.fromSiteNameCode(this, "Detoxing & Rehabilitation", "detox"),
                Board.fromSiteNameCode(this, "Personal Issues", "qq"),
		        Board.fromSiteNameCode(this, "Dream Discussion", "dr"),
		        Board.fromSiteNameCode(this, "Fitness", "ana"),
		        Board.fromSiteNameCode(this, "Food, Munchies & Cooking", "nom"),
		        Board.fromSiteNameCode(this, "Travel & Transportation", "vroom"),
		        Board.fromSiteNameCode(this, "Style & Travel", "st"),
		        Board.fromSiteNameCode(this, "Weapons Discussion", "nra"),
		        Board.fromSiteNameCode(this, "Sexuality Discussion", "sd"),
		        Board.fromSiteNameCode(this, "Transgender Discussion", "cd"),
		        Board.fromSiteNameCode(this, "Art & Oekaki", "art"),
		        Board.fromSiteNameCode(this, "Space... the Final Frontier", "sagan"),
		        Board.fromSiteNameCode(this, "World Languages", "lang"),
		        Board.fromSiteNameCode(this, "Science, Technology, Engineering & Mathematics", "stem"),
		        Board.fromSiteNameCode(this, "History Discussion", "his"),
		        Board.fromSiteNameCode(this, "Growing & Botany", "crops"),
		        Board.fromSiteNameCode(this, "Guides & Tutorials", "howto"),
		        Board.fromSiteNameCode(this, "Law Discussion", "law"),
		        Board.fromSiteNameCode(this, "Books & Literature", "lit"),
		        Board.fromSiteNameCode(this, "Medicine & Health", "med"),
		        Board.fromSiteNameCode(this, "Philosophy & Social Science", "pss"),
		        Board.fromSiteNameCode(this, "Computers & Tech Support", "tech"),
		        Board.fromSiteNameCode(this, "Programming", "prog"),
		        Board.fromSiteNameCode(this, "Star Trek Discussion", "1701"),
		        Board.fromSiteNameCode(this, "Sports", "sport"),
		        Board.fromSiteNameCode(this, "Movies & Television", "mtv"),
		        Board.fromSiteNameCode(this, "Flash", "f"),
	        	Board.fromSiteNameCode(this, "Music & Production", "m"),
	        	Board.fromSiteNameCode(this, "Mixed Martial Arts Discussion", "mma"),
                Board.fromSiteNameCode(this, "Comic & Web Comics Discussion", "616"),
	        	Board.fromSiteNameCode(this, "Anime & Manga Discussion", "a"),
	        	Board.fromSiteNameCode(this, "Professional Wrestling Discussion", "wooo"),
	        	Board.fromSiteNameCode(this, "World News", "n"),
		        Board.fromSiteNameCode(this, "Video Games Discussion", "vg"),
	        	Board.fromSiteNameCode(this, "Pokémon Discussion", "po"),
	        	Board.fromSiteNameCode(this, "Taditional Games", "tg"),
		        Board.fromSiteNameCode(this, "420chan Discussion & Staff Interaction", "420"),
		        Board.fromSiteNameCode(this, "Random & High Stuff", "b"),
		        Board.fromSiteNameCode(this, "Paranormal Discussion", "spooky"),
		        Board.fromSiteNameCode(this, "Dinosaur Discussion", "dino"),
		        Board.fromSiteNameCode(this, "Post-apocalyptic", "fo"),
		        Board.fromSiteNameCode(this, "Animal Discussion", "ani"),
		        Board.fromSiteNameCode(this, "Netjester AI Conversion Chamber", "nj"),
	        	Board.fromSiteNameCode(this, "Net Characters", "ns"),
	        	Board.fromSiteNameCode(this, "Conspiracy Theories", "tinfoil"),
	        	Board.fromSiteNameCode(this, "Dumb Wallpapers Below", "w")
        );

        setResolvable(URL_HANDLER);

        setConfig(new CommonConfig() {
            @Override
            public boolean feature(Feature feature) {
                return feature == Feature.POSTING;
            }
        });

        setEndpoints(new TaimabaEndpoints(this,
                "https://api.420chan.org",
                "https://boards.420chan.org"));
        setActions(new TaimabaActions(this));
        setApi(new TaimabaApi(this));
        setParser(new TaimabaCommentParser());
    }
}
