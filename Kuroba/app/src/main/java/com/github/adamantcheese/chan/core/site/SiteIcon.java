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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.BitmapResult;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

public class SiteIcon {
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
            NetUtils.makeBitmapRequest(url, new BitmapResult() {
                @Override
                public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                    Logger.e(SiteIcon.this, "Error loading favicon", e);
                    drawable = null;
                    BackgroundUtils.runOnMainThread(() -> res.onSiteIcon(new BitmapDrawable(getRes(),
                            BitmapRepository.error
                    )));
                }

                @Override
                public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                    drawable = new BitmapDrawable(getRes(), bitmap);
                    drawable.setFilterBitmap(false);
                    drawable.setDither(false);
                    BackgroundUtils.runOnMainThread(() -> res.onSiteIcon(drawable));
                }
            });
        }
    }

    public interface SiteIconResult {
        void onSiteIcon(Drawable icon);
    }
}
