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
package com.github.adamantcheese.chan.core.site;


import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.github.adamantcheese.chan.utils.Logger;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.injector;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class SiteIcon {
    private static final String TAG = "SiteIcon";
    private static final int FAVICON_SIZE = 64;

    private HttpUrl url;

    public static SiteIcon fromFavicon(HttpUrl url) {
        SiteIcon siteIcon = new SiteIcon();
        siteIcon.url = url;
        return siteIcon;
    }

    private SiteIcon() {
    }

    public void get(SiteIconResult result) {
        if (url != null) {
            injector().instance(ImageLoader.class).get(url.toString(), new ImageLoader.ImageListener() {
                @Override
                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                    if (response.getBitmap() != null) {
                        Drawable drawable = new BitmapDrawable(getAppContext().getResources(), response.getBitmap());
                        result.onSiteIcon(SiteIcon.this, drawable);
                    }
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                    Logger.e(TAG, "Error loading favicon", error);
                }
            }, FAVICON_SIZE, FAVICON_SIZE);
        }
    }

    public interface SiteIconResult {
        void onSiteIcon(SiteIcon siteIcon, Drawable icon);
    }
}
