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

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * ChanLoaderManager is a manager/factory for ChanLoaders. Only ChanLoaders for threads are cached.
 * Only one instance of this class should exist, and is dependency injected; as a result, the methods inside are synchronized.
 * <p>Each reference to a loader is a {@link ResponseResult<ChanThread>}, these references can be obtained with
 * {@link #obtain(Loadable, ResponseResult<ChanThread>)} and released with {@link #release(ChanThreadLoader, ResponseResult<ChanThread>)}.
 * A loader is only cached if it has no more listeners, therefore you can call {@link #obtain(Loadable, ResponseResult<ChanThread>)}
 * as many times as you want as long as you call release an equal amount of times.<br>
 * <br>
 * The internal cache here acts as a sort of cache for recently visited threads, preserving their already processed API
 * responses and allows threads to be returned quickly and switched between with minimal overhead.
 * <br>
 * In addition, this class acts as a sort of "reply draft" cache; this is effectively the only place that loadables should
 * have a constant reference to them held. As a result, reply drafts are available in the following situations:<br><br>
 * 1) You are currently viewing a thread. When you navigate away from that thread, it will be available until it is ejected
 * from the thread loader cache (ie you may visit up to {@link #THREAD_LOADERS_CACHE_SIZE} threads before your draft is
 * deleted). This is filter-watch safe ie filter watches won't prematurely remove your draft.<br>
 * 2) Pins have their loadables and drafts managed by {@link WatchManager} and are never erased.<br>
 * 3) You are writing up a new thread. If you navigate away from a board, YOUR DRAFT WILL BE DELETED. This is a result of
 * catalog loaders not being cached, which means their loadable (and therefore draft) is no longer referenced and garbage
 * collected as a result.
 */
public class ChanLoaderManager {
    public static final int THREAD_LOADERS_CACHE_SIZE = 25;

    //map between a loadable and a chan loader instance for it, currently in use
    private static final Map<Loadable, ChanThreadLoader> threadLoaders = new HashMap<>();
    //chan loader cache for released loadables
    private static final LruCache<Loadable, ChanThreadLoader> threadLoadersCache =
            new LruCache<>(THREAD_LOADERS_CACHE_SIZE);

    @NonNull
    public static synchronized ChanThreadLoader obtain(
            @NonNull Loadable loadable, ResponseResult<ChanThread> listener
    ) {
        BackgroundUtils.ensureMainThread();

        ChanThreadLoader chanLoader;
        if (loadable.isThreadMode()) {
            chanLoader = threadLoaders.get(loadable);
            if (chanLoader == null) {
                chanLoader = threadLoadersCache.get(loadable);
                if (chanLoader != null) {
                    threadLoadersCache.remove(loadable);
                    threadLoaders.put(loadable, chanLoader);
                }
            }

            if (chanLoader == null) {
                chanLoader = new ChanThreadLoader(loadable);
                threadLoaders.put(loadable, chanLoader);
            }
        } else {
            chanLoader = new ChanThreadLoader(loadable);
        }

        chanLoader.addListener(listener);

        return chanLoader;
    }

    public static synchronized void release(@NonNull ChanThreadLoader chanLoader, ResponseResult<ChanThread> listener) {
        BackgroundUtils.ensureMainThread();

        Loadable loadable = chanLoader.getLoadable();
        if (loadable.isThreadMode()) {
            ChanThreadLoader foundChanLoader = threadLoaders.get(loadable);
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
