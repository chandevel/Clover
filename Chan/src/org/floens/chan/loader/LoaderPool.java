package org.floens.chan.loader;

import java.util.HashMap;
import java.util.Map;

import org.floens.chan.model.Loadable;
import org.floens.chan.utils.Logger;

public class LoaderPool {
    private static final String TAG = "LoaderPool";
    
    private static LoaderPool instance;
    
    private static Map<Loadable, Loader> loaders = new HashMap<Loadable, Loader>();
    
    public static LoaderPool getInstance() {
        if (instance == null) {
            instance = new LoaderPool();
        }
        
        return instance;
    }
    
    public Loader obtain(Loadable loadable, Loader.LoaderListener listener) {
        Logger.d(TAG, "loaders size: " + loaders.size());
        
        Loader loader = loaders.get(loadable);
        if (loader == null) {
            loader = new Loader(loadable);
            loaders.put(loadable, loader);
        }
        
        loader.addListener(listener);
        
        return loader;
    }
    
    public void release(Loader loader, Loader.LoaderListener listener) {
        if (!loaders.containsValue(loader)) {
            throw new RuntimeException("The released loader does not exist");
        }
        
        if (loader.removeListener(listener)) {
            loaders.remove(loader.getLoadable());
        }
    }
}
