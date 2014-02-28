package org.floens.chan.watch;

import java.util.List;

import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;
import org.floens.chan.model.Post;
import org.floens.chan.net.ThreadLoader;
import org.floens.chan.service.PinnedService;
import org.floens.chan.utils.Logger;

import com.android.volley.VolleyError;

public class PinWatcher implements ThreadLoader.ThreadLoaderListener {
    private final ThreadLoader watchLoader;
    private final Loadable watchLoadable;
    
    private final Pin pin;
    
    private long startTime;

    public PinWatcher(Pin pin) {
        this.pin = pin;
        
        watchLoadable = pin.loadable.copy();
        watchLoadable.simpleMode = true;
        
        watchLoader = new ThreadLoader(this);
    }
    
    public void update() {
        Logger.test("PinWatcher update");
        
        startTime = System.currentTimeMillis();
        
        watchLoader.start(watchLoadable);
    }

    @Override
    public void onError(VolleyError error) {
        Logger.test("PinWatcher onError: ", error);
    }
    
    @Override
    public void onData(List<Post> result) {
        int count = result.size();
        
        Logger.i("PinWatcher onData");
        Logger.i("Post size: " + count);
        
        pin.watchNewCount = count;
        
        Logger.i("Load time: " + (System.currentTimeMillis() - startTime) + "ms");
        
        PinnedService.callOnPinsChanged();
    }
    
    
}
