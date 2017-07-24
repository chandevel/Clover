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
package org.floens.chan.chan;


import com.android.volley.Response;

import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.Post;

import java.util.List;

/**
 * A request from ChanLoader to load something.
 */
public class ChanLoaderRequestParams {
    /**
     * Related loadable for the request.
     */
    public final Loadable loadable;

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
                                   List<Post> cached,
                                   Response.Listener<ChanLoaderResponse> listener,
                                   Response.ErrorListener errorListener) {

        this.loadable = loadable;
        this.cached = cached;
        this.listener = listener;
        this.errorListener = errorListener;
    }
}
