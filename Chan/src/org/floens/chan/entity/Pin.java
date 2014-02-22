package org.floens.chan.entity;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.net.ThreadLoader;
import org.floens.chan.net.ThreadLoader.ThreadLoaderListener;

import com.android.volley.VolleyError;

public class Pin {
    public Type type = Type.THREAD;
    public Loadable loadable = new Loadable("", -1);
    
    private int count;
    private int newCount;
    private final boolean watch = true;
    private boolean error = false;
    private List<Post> postList = new ArrayList<Post>();
    public ThreadLoader threadLoader = new ThreadLoader(new ThreadLoaderListener() {
        @Override
        public void onError(VolleyError volleyError) {
            error = true;
        }
        
        @Override
        public void onData(List<Post> result) {
            postList = result;
            
            int totalCount = result.size();
            
            newCount = totalCount - count;
            count = totalCount;
        }
    });
    
    public void startLoading() {
        if (type != Type.THREAD || error) return;
        
        // threadLoader.start(new Loadable(board, no));   
    }
    
    public boolean getShouldWatch() {
        return watch;
    }
    
    public boolean getHasLoadingError() {
        return error;
    }
    
    public int getCount() {
        return count;
    }
    
    /**
     * Get how many new posts there are. Calling this will reset the internal 
     * counter to zero, so don't call this multiple times for new info.
     * @return Amount of new posts
     */
    public int getNewCount() {
        int newer = newCount;
        newCount = 0;
        
        return newer;
    }
    
    /** Header is used to display a static header in the drawer listview. */
    public static enum Type {
        HEADER, 
        THREAD
    };
}





