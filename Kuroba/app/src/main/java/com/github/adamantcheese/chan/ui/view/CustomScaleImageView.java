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
package com.github.adamantcheese.chan.ui.view;

import android.content.Context;
import android.graphics.RectF;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.adamantcheese.chan.utils.Logger;

public class CustomScaleImageView
        extends SubsamplingScaleImageView {
    private static final String TAG = "CustomScaleImageView";
    private static final float EPSILON = 0.00001f;

    private Callback callback;
    private final RectF panRectF = new RectF();

    public CustomScaleImageView(Context context) {
        super(context);
        setOnImageEventListener(new DefaultOnImageEventListener() {
            @Override
            public void onReady() {
                Logger.d(TAG, "onReady");
                float scale = Math.min(getWidth() / (float) getSWidth(), getHeight() / (float) getSHeight());

                if (getMaxScale() < scale * 2f) {
                    setMaxScale(scale * 2f);
                }

                setMinimumScaleType(SCALE_TYPE_CUSTOM);
                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onImageLoaded() {
                Logger.d(TAG, "onImageLoaded");
                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onImageLoadError(Exception e) {
                Logger.w(TAG, "onImageLoadError", e);
                if (callback != null) {
                    callback.onError(true);
                }
            }

            @Override
            public void onTileLoadError(Exception e) {
                Logger.w(TAG, "onTileLoadError", e);
                if (callback != null) {
                    callback.onError(false);
                }
            }
        });
    }

    public boolean isViewportTouchingImageBottom() {
        panRectF.set(0f, 0f, 0f, 0f);
        getPanRemaining(panRectF);

        return Math.abs(panRectF.bottom) < EPSILON;
    }

    public boolean isViewportTouchingImageTop() {
        panRectF.set(0f, 0f, 0f, 0f);
        getPanRemaining(panRectF);

        return Math.abs(panRectF.top) < EPSILON;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onReady();

        void onError(boolean wasInitial);
    }
}
