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

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

public class SiteIcon {
    private static final int FAVICON_SIZE = 64;

    private HttpUrl url;
    private Drawable drawable;

    public static SiteIcon fromFavicon(HttpUrl url) {
        SiteIcon siteIcon = new SiteIcon();
        siteIcon.url = url;
        return siteIcon;
    }

    public static SiteIcon fromDrawable(Drawable drawable) {
        SiteIcon siteIcon = new SiteIcon();
        siteIcon.drawable = drawable;
        return siteIcon;
    }

    private SiteIcon() {
    }

    public void get(final SiteIconResult res) {
        if (drawable != null) {
            res.onSiteIcon(drawable);
        } else if (url != null) {
            NetUtils.makeBitmapRequest(url, new NetUtils.BitmapResult() {
                @Override
                public void onBitmapFailure(Bitmap errormap) {
                    Logger.e(SiteIcon.this, "Error loading favicon");
                    drawable = null;
                    BackgroundUtils.runOnMainThread(() -> res.onSiteIcon(new BitmapDrawable(getRes(), errormap)));
                }

                @Override
                public void onBitmapSuccess(Bitmap bitmap) {
                    drawable = new BitmapDrawable(getRes(), bitmap);
                    BackgroundUtils.runOnMainThread(() -> res.onSiteIcon(drawable));
                }
            }, FAVICON_SIZE, FAVICON_SIZE);
        }
    }

    public interface SiteIconResult {
        void onSiteIcon(Drawable icon);
    }
}
