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

            // long start = System.currentTimeMillis();

            T read = readJson(reader);

            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Log.e("Chan", "Total time: " + (System.currentTimeMillis() - start));

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
