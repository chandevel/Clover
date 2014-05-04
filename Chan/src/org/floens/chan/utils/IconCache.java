/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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

import org.floens.chan.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class IconCache {
    public static Bitmap stickyIcon;
    public static Bitmap closedIcon;

    /**
     * Load the icons in the cache. Lightweight icons only! Icons can be null!
     * 
     * @param context
     */
    public static void createIcons(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                stickyIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.sticky_icon);
                closedIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_icon);
            }
        }).start();
    }
}
