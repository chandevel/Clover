/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.pool;

import android.util.LruCache;

import org.floens.chan.chan.ChanLoader;
import org.floens.chan.core.model.Loadable;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * ChanLoaderFactory is a factory for ChanLoaders. ChanLoaders for threads are cached.
 * <p>Each reference to a loader is a {@link org.floens.chan.chan.ChanLoader.ChanLoaderCallback}, these
 * references can be obtained with {@link #obtain(Loadable, ChanLoader.ChanLoaderCallback)}} and released
 * with {@link #release(ChanLoader, ChanLoader.ChanLoaderCallback)}.
 */
@Singleton
public class ChanLoaderFactory {
    // private static final String TAG = "ChanLoaderFactory";
    public static final int THREAD_LOADERS_CACHE_SIZE = 25;

    private Map<Loadable, ChanLoader> threadLoaders = new HashMap<>();
    private LruCache<Loadable, ChanLoader> threadLoadersCache = new LruCache<>(THREAD_LOADERS_CACHE_SIZE);

    @Inject
    public ChanLoaderFactory() {
    }

    public ChanLoader obtain(Loadable loadable, ChanLoader.ChanLoaderCallback listener) {
        ChanLoader chanLoader;
        if (loadable.isThreadMode()) {
            if (!loadable.isFromDatabase()) {
                throw new IllegalArgumentException();
            }

            chanLoader = threadLoaders.get(loadable);
            if (chanLoader == null) {
                chanLoader = threadLoadersCache.get(loadable);
                if (chanLoader != null) {
                    threadLoadersCache.remove(loadable);
                    threadLoaders.put(loadable, chanLoader);
                }
            }

            if (chanLoader == null) {
                chanLoader = new ChanLoader(loadable);
                threadLoaders.put(loadable, chanLoader);
            }
        } else {
            chanLoader = new ChanLoader(loadable);
        }

        chanLoader.addListener(listener);

        return chanLoader;
    }

    public void release(ChanLoader chanLoader, ChanLoader.ChanLoaderCallback listener) {
        Loadable loadable = chanLoader.getLoadable();
        if (loadable.isThreadMode()) {
            ChanLoader foundChanLoader = threadLoaders.get(loadable);

            if (foundChanLoader == null) {
                throw new IllegalStateException("The released loader does not exist");
            }

            if (chanLoader.removeListener(listener)) {
                threadLoaders.remove(loadable);
                threadLoadersCache.put(loadable, chanLoader);
            }
        } else {
            chanLoader.removeListener(listener);
        }
    }
}
