package org.floens.chan.loader;

import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Post;
import org.floens.chan.utils.Logger;

import android.util.SparseArray;

import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

public class ThreadLoader {
    private static final String TAG = "ThreadLoader";
    
    private final ThreadLoaderListener listener;
    private ChanReaderRequest loader;
    private boolean stopped = false;
    private boolean loading = false;
    private Loadable loadable;
    private final SparseArray<Post> postsById = new SparseArray<Post>();
    
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
        Logger.i(TAG, "Start loading " + loadable.board + ", " + loadable.no);
        
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
//            Logger.i(TAG, "Stop loading");
            loader.cancel();
            loader = null;
        }
        
        postsById.clear();
        
        stopped = true;
    }
    
    public Post getPostById(int id) {
        return postsById.get(id);
    }
    
    private ChanReaderRequest getData(Loadable loadable) {
        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, new Response.Listener<List<Post>>() {
            @Override
            public void onResponse(List<Post> list) {
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
    
    private void onData(List<Post> result) {
        if (stopped) return;
        
        for (Post post : result) {
            postsById.append(post.no, post);
        }
        
        listener.onData(result);
    }
    
    private void onError(VolleyError error) {
        if (stopped) return;
        
        Logger.e(TAG, "Error loading" + error.getMessage(), error);
        
        // 404 with more pages already loaded means endofline
        if ((error instanceof ServerError) && loadable.isBoardMode() && loadable.no > 0) {
            error = new EndOfLineException();
        }
        
        listener.onError(error);
    }
    
    public static abstract interface ThreadLoaderListener {
        public abstract void onData(List<Post> result);
        public abstract void onError(VolleyError error);
    }
}





