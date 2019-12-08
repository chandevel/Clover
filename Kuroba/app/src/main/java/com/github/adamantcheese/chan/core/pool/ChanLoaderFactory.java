/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.pool;

import android.util.LruCache;

import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * ChanLoaderFactory is a factory for ChanLoaders. ChanLoaders for threads are cached.
 * <p>Each reference to a loader is a {@link ChanThreadLoader.ChanLoaderCallback}, these
 * references can be obtained with {@link #obtain(Loadable, WatchManager, ChanThreadLoader.ChanLoaderCallback)}} and released
 * with {@link #release(ChanThreadLoader, ChanThreadLoader.ChanLoaderCallback)}.
 */
public class ChanLoaderFactory {
    private static final String TAG = "ChanLoaderFactory";
    public static final int THREAD_LOADERS_CACHE_SIZE = 25;

    private Map<Loadable, ChanThreadLoader> threadLoaders = new HashMap<>();
    private LruCache<Loadable, ChanThreadLoader> threadLoadersCache = new LruCache<>(THREAD_LOADERS_CACHE_SIZE);

    public ChanThreadLoader obtain(
            Loadable loadable, WatchManager watchManager, ChanThreadLoader.ChanLoaderCallback listener
    ) {
        ChanThreadLoader chanLoader;
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
                chanLoader = new ChanThreadLoader(loadable, watchManager);
                threadLoaders.put(loadable, chanLoader);
            }
        } else {
            chanLoader = new ChanThreadLoader(loadable, watchManager);
        }

        chanLoader.addListener(listener);

        return chanLoader;
    }

    public void release(ChanThreadLoader chanLoader, ChanThreadLoader.ChanLoaderCallback listener) {
        Loadable loadable = chanLoader.getLoadable();
        if (loadable.isThreadMode()) {
            ChanThreadLoader foundChanLoader = threadLoaders.get(loadable);

            if (foundChanLoader == null) {
                Logger.wtf(TAG, "Loader doesn't exist.");
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
