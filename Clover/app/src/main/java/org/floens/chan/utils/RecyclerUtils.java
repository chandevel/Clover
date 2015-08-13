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
package org.floens.chan.utils;

import android.support.v7.widget.RecyclerView;

import java.lang.reflect.Field;

public class RecyclerUtils {
    private static final String TAG = "RecyclerUtils";

    public static void clearRecyclerCache(RecyclerView recyclerView) {
        try {
            Field field = RecyclerView.class.getDeclaredField("mRecycler");
            field.setAccessible(true);
            RecyclerView.Recycler recycler = (RecyclerView.Recycler) field.get(recyclerView);
            recycler.clear();
        } catch (Exception e) {
            Logger.e(TAG, "Error clearing RecyclerView cache with reflection", e);
        }
    }
}
