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
package com.github.adamantcheese.chan.core.site.loader;


import com.android.volley.Response;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.parser.ChanReader;

import java.util.List;

/**
 * A request from ChanThreadLoader to load something.
 */
public class ChanLoaderRequestParams {
    /**
     * Related loadable for the request.
     */
    public final Loadable loadable;

    public final ChanReader chanReader;

    /**
     * Cached Post objects from previous loads, or an empty list.
     */
    public final List<Post> cached;

    /**
     * Success listener.
     */
    public final Response.Listener<ChanLoaderResponse> listener;

    /**
     * Error listener.
     */
    public final Response.ErrorListener errorListener;

    public ChanLoaderRequestParams(Loadable loadable,
                                   ChanReader chanReader,
                                   List<Post> cached,
                                   Response.Listener<ChanLoaderResponse> listener,
                                   Response.ErrorListener errorListener) {

        this.loadable = loadable;
        this.chanReader = chanReader;
        this.cached = cached;
        this.listener = listener;
        this.errorListener = errorListener;
    }
}
