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
package org.floens.chan.core.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import android.util.JsonReader;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

public abstract class JsonReaderRequest<T> extends Request<T> {
    protected final Listener<T> listener;
    private VolleyError error;

    public JsonReaderRequest(String url, Listener<T> listener, ErrorListener errorListener) {
        super(Method.GET, url, errorListener);

        this.listener = listener;
    }

    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    public void setError(VolleyError error) {
        this.error = error;
    }

    @Override
    protected Response<T> parseNetworkResponse(NetworkResponse response) {
        try {
            ByteArrayInputStream baos = new ByteArrayInputStream(response.data);

            JsonReader reader = new JsonReader(new InputStreamReader(baos, "UTF-8"));

            T read = readJson(reader);

            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (read == null) {
                return Response.error(new VolleyError());
            } else if (error != null) {
                return Response.error(error);
            } else {
                return Response.success(read, HttpHeaderParser.parseCacheHeaders(response));
            }
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        }
    }

    public abstract T readJson(JsonReader reader);
}
