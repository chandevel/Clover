/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
import android.util.AttributeSet;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import com.github.adamantcheese.chan.utils.Logger;

public class CustomScaleImageView extends SubsamplingScaleImageView {
    private static final String TAG = "CustomScaleImageView";

    private Callback callback;

    public CustomScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }

    public CustomScaleImageView(Context context) {
        super(context);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void init() {
        setOnImageEventListener(new OnImageEventListener() {
            @Override
            public void onReady() {
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
            }

            @Override
            public void onPreviewLoadError(Exception e) {
            }

            @Override
            public void onPreviewReleased() {
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

    public interface Callback {
        void onReady();

        void onError(boolean wasInitial);
    }
}
