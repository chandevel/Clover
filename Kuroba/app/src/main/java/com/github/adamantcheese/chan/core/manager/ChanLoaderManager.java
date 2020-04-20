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
package com.github.adamantcheese.chan.core.manager;

import android.util.LruCache;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader.ChanLoaderCallback;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.github.adamantcheese.chan.Chan.instance;

/**
 * ChanLoaderManager is a manager/factory for ChanLoaders. ChanLoaders for threads are cached.
 * Only one instance of this class should exist, and is dependency injected; as a result, the methods inside are synchronized.
 * <p>Each reference to a loader is a {@link ChanLoaderCallback}, these references can be obtained with
 * {@link #obtain(Loadable, ChanLoaderCallback)} and released with {@link #release(ChanThreadLoader, ChanLoaderCallback)}.
 * A loader is only cached if it has no more listeners, therefore you can call {@link #obtain(Loadable, ChanLoaderCallback)}
 * as many times as you want as long as you call release an equal amount of times.
 */
public class ChanLoaderManager {
    public static final int THREAD_LOADERS_CACHE_SIZE = 25;

    //map between a loadable and a chan loader instance for it, currently in use
    private Map<Loadable, ChanThreadLoader> threadLoaders = new HashMap<>();
    //chan loader cache for released loadables
    private LruCache<Loadable, ChanThreadLoader> threadLoadersCache = new LruCache<>(THREAD_LOADERS_CACHE_SIZE);

    @NonNull
    public synchronized ChanThreadLoader obtain(@NonNull Loadable loadable, ChanLoaderCallback listener) {
        BackgroundUtils.ensureMainThread();
        WatchManager watchManager = instance(WatchManager.class);

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

    public synchronized void release(@NonNull ChanThreadLoader chanLoader, ChanLoaderCallback listener) {
        BackgroundUtils.ensureMainThread();

        Loadable loadable = chanLoader.getLoadable();
        if (loadable.isThreadMode()) {
            ChanThreadLoader foundChanLoader = threadLoaders.get(loadable);
            if (foundChanLoader == null) {
                Logger.wtf(this, "Loader doesn't exist.");
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
