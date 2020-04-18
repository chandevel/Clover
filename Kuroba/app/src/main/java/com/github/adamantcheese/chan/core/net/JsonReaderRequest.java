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

import android.util.JsonReader;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.github.adamantcheese.chan.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class JsonReaderRequest<T>
        extends Request<T> {
    private static final Charset UTF8 = StandardCharsets.UTF_8;

    protected final Listener<T> listener;

    public JsonReaderRequest(String url, Listener<T> listener, ErrorListener errorListener) {
        super(Method.GET, url, errorListener);

        this.listener = listener;
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        ByteArrayInputStream baos = new ByteArrayInputStream(response.data);
        JsonReader reader = new JsonReader(new InputStreamReader(baos, UTF8));

        Exception exception = null;
        T read = null;
        try {
            read = readJson(reader);
        } catch (Exception e) {
            exception = e;
        }

        IOUtils.closeQuietly(reader);

        if (read == null) {
            if (exception != null) {
                return Response.error(new VolleyError(exception));
            } else {
                return Response.error(new VolleyError("Unknown error"));
            }
        } else {
            return Response.success(read, HttpHeaderParser.parseCacheHeaders(response));
        }
    }

    /**
     * Read your json. Returning null or throwing something means a Response.error, Response.success is returned otherwise.
     * The reader is closed for you.
     *
     * @param reader A json reader to use
     * @return null or the data
     *
     * @throws Exception none or an exception
     */
    public abstract T readJson(JsonReader reader)
            throws Exception;
}
