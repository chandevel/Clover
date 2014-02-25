package org.floens.chan.model;

import java.util.List;

import org.floens.chan.net.ThreadLoader;
import org.floens.chan.utils.Logger;

import com.android.volley.VolleyError;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pin implements ThreadLoader.ThreadLoaderListener {
    // Database stuff
    @DatabaseField(generatedId = true)
    private int id;
    
    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable = new Loadable("", -1);
    
    // ListView Stuff
    /** Header is used to display a static header in the drawer listview. */
    public Type type = Type.THREAD;
    public static enum Type {
        HEADER, 
        THREAD
    };
    
    // PinnedService stuff
    public ThreadLoader threadLoader;
    public int lastPostCount;
    public int newPostCount;
    
    public void update() {
        Logger.test("Update in pin");
        
        if (threadLoader == null) {
            threadLoader = new ThreadLoader(this);
        }
        
        threadLoader.start(loadable);
    }
    
    @Override
    public void onError(VolleyError error) {
        Logger.test("OnError in pin: ", error);
    }
    
    @Override
    public void onData(List<Post> result) {
        Logger.test("OnData in pin: ");
        Logger.test("Size: " + result.size());
        
        newPostCount = result.size();
    }
}





