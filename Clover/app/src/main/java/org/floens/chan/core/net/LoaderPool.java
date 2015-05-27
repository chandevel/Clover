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
package org.floens.chan.core.net;

import org.floens.chan.chan.ChanLoader;
import org.floens.chan.core.model.Loadable;

import java.util.HashMap;
import java.util.Map;

public class LoaderPool {
    //    private static final String TAG = "LoaderPool";

    private static LoaderPool instance = new LoaderPool();

    private static Map<Loadable, ChanLoader> loaders = new HashMap<>();

    public static LoaderPool getInstance() {
        return instance;
    }

    public ChanLoader obtain(Loadable loadable, ChanLoader.ChanLoaderCallback listener) {
        ChanLoader chanLoader = loaders.get(loadable);
        if (chanLoader == null) {
            chanLoader = new ChanLoader(loadable);
            loaders.put(loadable, chanLoader);
        }

        chanLoader.addListener(listener);

        return chanLoader;
    }

    public void release(ChanLoader chanLoader, ChanLoader.ChanLoaderCallback listener) {
        ChanLoader foundChanLoader = null;
        for (Loadable l : loaders.keySet()) {
            if (chanLoader.getLoadable().equals(l)) {
                foundChanLoader = loaders.get(l);
                break;
            }
        }

        if (foundChanLoader == null) {
            throw new RuntimeException("The released loader does not exist");
        }

        if (chanLoader.removeListener(listener)) {
            loaders.remove(chanLoader.getLoadable());
        }
    }
}
