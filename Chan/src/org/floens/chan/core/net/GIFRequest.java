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

import org.floens.chan.ui.view.GIFView;

import android.content.Context;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

public class GIFRequest extends Request<GIFView> {
    protected final Listener<GIFView> listener;
    private final Context context;

    public GIFRequest(String url, Listener<GIFView> listener, ErrorListener errorListener, Context context) {
        super(Method.GET, url, errorListener);

        this.listener = listener;
        this.context = context;
    }

    @Override
    protected void deliverResponse(GIFView response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<GIFView> parseNetworkResponse(NetworkResponse response) {
        GIFView gifView = new GIFView(context);
        boolean success = gifView.setData(response.data);

        if (success) {
            return Response.success(gifView, HttpHeaderParser.parseCacheHeaders(response));
        } else {
            return Response.error(new ParseError());
        }
    }
}
