/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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
package org.floens.chan.core.net;

import java.io.File;

import org.floens.chan.ChanApplication;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpHeaderParser;

public class FileRequest extends Request<Void> {
    protected final Listener<File> listener;

    public FileRequest(String url, Listener<File> listener, ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        this.listener = listener;

        setShouldCache(true);
    }

    @Override
    protected Response<Void> parseNetworkResponse(NetworkResponse response) {
        return Response.success(null, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(Void response) {
        DiskBasedCache cache = (DiskBasedCache) ChanApplication.getVolleyRequestQueue().getCache();
        File file = cache.getFileForKey(getCacheKey());

        if (file.exists()) {
            listener.onResponse(file);
        } else {
            listener.onResponse(null);
        }
    }
}
