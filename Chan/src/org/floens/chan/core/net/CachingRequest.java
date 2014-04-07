package org.floens.chan.core.net;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

public class CachingRequest extends Request<Void> {
    protected final Listener<Void> listener;

    public CachingRequest(String url, Listener<Void> listener, ErrorListener errorListener) {
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
        listener.onResponse(response);
    }
}
