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
package org.floens.chan.core.site;

import android.support.v4.util.ArrayMap;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;

import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Endpoints for {@link Site}.
 */
public interface SiteEndpoints {
    HttpUrl catalog(Board board);

    HttpUrl thread(Board board, Loadable loadable);

    HttpUrl imageUrl(Post.Builder post, Map<String, String> arg);

    HttpUrl thumbnailUrl(Post.Builder post, boolean spoiler, Map<String, String> arg);

    HttpUrl icon(String icon, Map<String, String> arg);

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

    static Map<String, String> makeArgument(String key1, String value1,
                                            String key2, String value2) {
        Map<String, String> map = new ArrayMap<>(2);
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }
}
