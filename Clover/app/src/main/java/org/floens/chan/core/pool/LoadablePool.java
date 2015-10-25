package org.floens.chan.core.pool;

import org.floens.chan.core.model.Loadable;

import java.util.HashMap;
import java.util.Map;

public class LoadablePool {
    private static final LoadablePool instance = new LoadablePool();

    private Map<Loadable, Loadable> pool = new HashMap<>();

    private LoadablePool() {
    }

    public static LoadablePool getInstance() {
        return instance;
    }

    public Loadable obtain(Loadable searchLoadable) {
        if (searchLoadable.isThreadMode()) {
            Loadable poolLoadable = pool.get(searchLoadable);
            if (poolLoadable == null) {
                poolLoadable = searchLoadable;
                pool.put(poolLoadable, poolLoadable);
            }

            return poolLoadable;
        } else {
            return searchLoadable;
        }
    }
}
