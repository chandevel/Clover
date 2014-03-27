package org.floens.chan.core.net;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

public class ByteArrayRequest extends Request<byte[]> {
    protected final Listener<byte[]> listener;
    
    public ByteArrayRequest(String url, Listener<byte[]> listener, ErrorListener errorListener) {
        super(Method.GET, url, errorListener);
        
        this.listener = listener;
    }
    
    @Override
    protected void deliverResponse(byte[] response) {
        listener.onResponse(response);
    }
    
    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        return Response.success(response.data, null);
    }
}

