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

import androidx.collection.ArrayMap;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.PassthroughBitmapResult;

import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Endpoints for {@link Site}.
 */
public interface SiteEndpoints {

    HttpUrl catalog(Board board);

    HttpUrl thread(Loadable loadable);

    HttpUrl imageUrl(Post.Builder post, Map<String, String> arg);

    HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg);

    enum ICON_TYPE {
        COUNTRY_FLAG,
        BOARD_FLAG,
        SINCE4PASS,
        OTHER
    }

    Pair<HttpUrl, PassthroughBitmapResult> icon(ICON_TYPE icon, Map<String, String> arg);

    HttpUrl boards();

    HttpUrl pages(Board board);

    HttpUrl archive(Board board);

    HttpUrl reply(Loadable thread);

    HttpUrl delete(Post post);

    HttpUrl report(Post post);

    HttpUrl login();

    static Map<String, String> makeArgument(String key, String value) {
        Map<String, String> map = new ArrayMap<>(1);
        map.put(key, value);
        return map;
    }

    static Map<String, String> makeArgument(String key1, String value1, String key2, String value2) {
        Map<String, String> map = new ArrayMap<>(2);
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
