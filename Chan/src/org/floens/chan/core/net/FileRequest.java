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
