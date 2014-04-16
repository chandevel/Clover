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
