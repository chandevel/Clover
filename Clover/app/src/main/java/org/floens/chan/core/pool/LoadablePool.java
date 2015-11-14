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
