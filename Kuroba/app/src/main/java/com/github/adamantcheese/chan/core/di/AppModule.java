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
package com.github.adamantcheese.chan.core.di;

import android.app.NotificationManager;
import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;
import com.github.adamantcheese.chan.core.net.BitmapLruImageCache;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.ui.captcha.CaptchaHolder;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

import static android.content.Context.NOTIFICATION_SERVICE;

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
    public ImageLoader provideImageLoader(RequestQueue requestQueue,
                                          Context applicationContext,
                                          ThemeHelper themeHelper) {
        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;
        return new ImageLoader(
                applicationContext,
                requestQueue,
                themeHelper,
                new BitmapLruImageCache(lruImageCacheSize));
    }

    @Provides
    @Singleton
    public NotificationManager provideNotificationManager() {
        return (NotificationManager) applicationContext.getSystemService(NOTIFICATION_SERVICE);
    }

    @Provides
    @Singleton
    public ThemeHelper provideThemeHelper() {
        return new ThemeHelper();
    }

    @Provides
    @Singleton
    public ImageSaver provideImageSaver() {
        return new ImageSaver();
    }

    @Provides
    @Singleton
    public CaptchaHolder provideCaptchaHolder() {
        return new CaptchaHolder();
    }
}
