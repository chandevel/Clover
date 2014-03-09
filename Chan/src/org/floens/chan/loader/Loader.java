package org.floens.chan.loader;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Post;
import org.floens.chan.utils.Logger;

import android.util.SparseArray;

import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

public class Loader {
    private static final String TAG = "Loader";
    
    private final List<LoaderListener> listeners = new ArrayList<LoaderListener>();
    private final Loadable loadable;
    private ChanReaderRequest request;
    private boolean destroyed = false;
    
    private final SparseArray<Post> postsById = new SparseArray<Post>();
    
    public Loader(Loadable loadable) {
        this.loadable = loadable;
    }
    
    /**
     * Add a LoaderListener
     * @param l the listener to add
     */
    public void addListener(LoaderListener l) {
        listeners.add(l);
    }
    
    /**
     * Remove a LoaderListener
     * @param l the listener to remove
     * @return true if there are no more listeners, false otherwise
     */
    public boolean removeListener(LoaderListener l) {
        listeners.remove(l);
        if (listeners.size() == 0) {
            destroyed = true;
            return true;
        } else {
            return false;
        }
    }
    
    public void requestData() {
        if (request != null) {
            request.cancel();
        }
        
        postsById.clear();
        
        if (loadable.isBoardMode()) {
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
        }
        
        request = getData(loadable);
    }
    
    public void requestNextData() {
        if (loadable.isBoardMode()) {
            loadable.no++;
            
            if (request != null) {
                request.cancel();
            }
            
            request = getData(loadable);
        }
    }
    
    /**
     * @return Returns if this loader is currently loading
     */
    public boolean isLoading() {
        return request != null;
    }
    
    public Post getPostById(int id) {
        return postsById.get(id);
    }
    
    public Loadable getLoadable() {
        return loadable;
    }
    
    private ChanReaderRequest getData(Loadable loadable) {
        Logger.i(TAG, "Requested " + loadable.board + ", " + loadable.no);
        
        ChanReaderRequest request = ChanReaderRequest.newInstance(loadable, new Response.Listener<List<Post>>() {
            @Override
            public void onResponse(List<Post> list) {
                Loader.this.request = null;
                onData(list);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Loader.this.request = null;
                onError(error);
            }
        });
        
        ChanApplication.getVolleyRequestQueue().add(request);
        
        return request;
    }
    
    private void onData(List<Post> result) {
        if (destroyed) return;
        
        for (Post post : result) {
            postsById.append(post.no, post);
        }
        
        for (LoaderListener l : listeners) {
            l.onData(result);
        }
    }
    
    private void onError(VolleyError error) {
        if (destroyed) return;
        
        Logger.e(TAG, "Error loading " + error.getMessage(), error);
        
        // 404 with more pages already loaded means endofline
        if ((error instanceof ServerError) && loadable.isBoardMode() && loadable.no > 0) {
            error = new EndOfLineException();
        }
        
        for (LoaderListener l : listeners) {
            l.onError(error);
        }
    }
    
    public static interface LoaderListener {
        public void onData(List<Post> result);
        public void onError(VolleyError error);
    }
}





