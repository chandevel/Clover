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
package org.floens.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;

import org.codejargon.feather.Provides;
import org.floens.chan.core.net.BitmapLruImageCache;

import javax.inject.Singleton;

public class AppModule {
    private Context applicationContext;

    public AppModule(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Provides
    @Singleton
    public Context provideApplicationContext() {
        return applicationContext;
    }

    @Provides
    @Singleton
    public ImageLoader provideImageLoader(RequestQueue requestQueue) {
        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;
        return new ImageLoader(requestQueue, new BitmapLruImageCache(lruImageCacheSize));
    }
}
