package org.floens.chan.net;

import java.util.ArrayList;

import org.floens.chan.ChanApplication;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Post;

import android.util.Log;

import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

public class ThreadLoader {
    private final ThreadLoaderListener listener;
    private ChanReaderRequest loader;
    private boolean stopped = false;
    private boolean loading = false;
    private Loadable loadable;
    
    public ThreadLoader(ThreadLoaderListener listener) {
        this.listener = listener;
    }
    
    /**
     * @return Returns if this loader is currently loading
     */
    public boolean isLoading() {
        return loading;
    }
    
    // public void start(int mode, String board, int pageOrThreadId) {
    public void start(Loadable loadable) {
        stop();
        stopped = false;
        
        this.loadable = loadable;
        loader = getData(loadable);
    }
    
    public void loadMore() {
        if (loadable.isBoardMode()) {
            loadable.no++;
            start(loadable);
        }
    }
    
    public void stop() {
        if (loader != null) {
            loader.cancel();
            loader = null;
        }
        
        stopped = true;
    }
    
    private ChanReaderRequest getData(Loadable loadable) {
        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, new Response.Listener<ArrayList<Post>>() {
            @Override
            public void onResponse(ArrayList<Post> list) {
                loading = false;
                onData(list);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                loading = false;
                onError(error);
            }
        });
        
        ChanApplication.getVolleyRequestQueue().add(request);
        loading = true;
        
        return request;
    }
    
    private void onData(ArrayList<Post> result) {
        if (stopped) return;
        
        listener.onData(result);
    }
    
    private void onError(VolleyError error) {
        if (stopped) return;
        
        Log.e("Chan", "VolleyError: " + error.getMessage());
        
        // 404 with more pages already loaded means endofline
        if ((error instanceof ServerError) && loadable.isBoardMode() && loadable.no > 0) {
            error = new EndOfLineException();
        }
        
        listener.onError(error);
    }
    
    public static abstract class ThreadLoaderListener {
        public abstract void onData(ArrayList<Post> result);
        public abstract void onError(VolleyError error);
    }
}





