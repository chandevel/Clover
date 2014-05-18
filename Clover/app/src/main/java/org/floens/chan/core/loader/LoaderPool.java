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
package org.floens.chan.core.loader;

import java.util.HashMap;
import java.util.Map;

import org.floens.chan.core.model.Loadable;

public class LoaderPool {
    //    private static final String TAG = "LoaderPool";

    private static LoaderPool instance;

    private static Map<Loadable, Loader> loaders = new HashMap<Loadable, Loader>();

    public static LoaderPool getInstance() {
        if (instance == null) {
            instance = new LoaderPool();
        }

        return instance;
    }

    public Loader obtain(Loadable loadable, Loader.LoaderListener listener) {
        Loader loader = null;
        for (Loadable l : loaders.keySet()) {
            if (loadable.equals(l)) {
                loader = loaders.get(l);
                break;
            }
        }

        if (loader == null) {
            loader = new Loader(loadable);
            loaders.put(loadable, loader);
        }

        loader.addListener(listener);

        return loader;
    }

    public void release(Loader loader, Loader.LoaderListener listener) {
        Loader foundLoader = null;
        for (Loadable l : loaders.keySet()) {
            if (loader.getLoadable().equals(l)) {
                foundLoader = loaders.get(l);
                break;
            }
        }

        if (foundLoader == null) {
            throw new RuntimeException("The released loader does not exist");
        }

        if (loader.removeListener(listener)) {
            loaders.remove(loader.getLoadable());
        }
    }
}
