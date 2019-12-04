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
package com.github.adamantcheese.chan.core.net;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public abstract class HtmlReaderRequest<T>
        extends Request<T> {
    protected final Listener<T> listener;

    public HtmlReaderRequest(String url, Listener<T> listener, Response.ErrorListener errorListener) {
        super(Request.Method.GET, url, errorListener);

        this.listener = listener;
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            Document document = Jsoup.parse(new ByteArrayInputStream(response.data),
                    HttpHeaderParser.parseCharset(response.headers),
                    getUrl()
            );

            T result = readDocument(document);

            return Response.success(result, HttpHeaderParser.parseCacheHeaders(response));
        } catch (IOException e) {
            return Response.error(new VolleyError(e));
        }
    }

    public abstract T readDocument(Document document);
}
