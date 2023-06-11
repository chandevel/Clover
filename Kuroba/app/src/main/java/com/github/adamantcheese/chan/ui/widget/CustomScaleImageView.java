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
package com.github.adamantcheese.chan.ui.widget;

import android.content.Context;
import android.graphics.RectF;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.adamantcheese.chan.utils.Logger;

public class CustomScaleImageView
        extends SubsamplingScaleImageView {
    private static final float EPSILON = 0.00001f;

    private Callback callback;
    private final RectF panRectF = new RectF();

    public CustomScaleImageView(Context context) {
        super(context);
        setOnImageEventListener(new DefaultOnImageEventListener() {
            @Override
            public void onReady() {
                Logger.vd(CustomScaleImageView.this, "onReady");
                int vPadding = getPaddingBottom() + getPaddingTop();
                int hPadding = getPaddingLeft() + getPaddingRight();
                // this scale value is what makes the image fill the view by default
                float scale = Math.min((getWidth() - hPadding) / (float) getSWidth(),
                        (getHeight() - vPadding) / (float) getSHeight()
                );
                if (getMaxScale() < scale * 2f) {
                    setMaxScale(scale * 2f);
                }
                setMinimumScaleType(SCALE_TYPE_CUSTOM);
            }

            @Override
            public void onImageLoaded() {
                Logger.vd(CustomScaleImageView.this, "onImageLoaded");
                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onImageLoadError(Exception e) {
                Logger.w(CustomScaleImageView.this, "onImageLoadError", e);
                if (callback != null) {
                    callback.onError(e, true);
                }
            }

            @Override
            public void onTileLoadError(Exception e) {
                Logger.w(CustomScaleImageView.this, "onTileLoadError", e);
                if (callback != null) {
                    callback.onError(e, false);
                }
            }
        });
    }

    public ImageViewportTouchSide getImageViewportTouchSide() {
        int side = 0;

        panRectF.set(0f, 0f, 0f, 0f);
        getPanRemaining(panRectF);

        if (Math.abs(panRectF.left) < EPSILON) {
            side = side | ImageViewportTouchSide.LEFT_SIDE;
        }
        if (Math.abs(panRectF.right) < EPSILON) {
            side = side | ImageViewportTouchSide.RIGHT_SIDE;
        }
        if (Math.abs(panRectF.top) < EPSILON) {
            side = side | ImageViewportTouchSide.TOP_SIDE;
        }
        if (Math.abs(panRectF.bottom) < EPSILON) {
            side = side | ImageViewportTouchSide.BOTTOM_SIDE;
        }

        return new ImageViewportTouchSide(side);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @SuppressWarnings("unused")
    public static class ImageViewportTouchSide {
        @SuppressWarnings("PointlessBitwiseExpression")
        public final static int LEFT_SIDE = 1 << 0;
        public final static int RIGHT_SIDE = 1 << 1;
        public final static int TOP_SIDE = 1 << 2;
        public final static int BOTTOM_SIDE = 1 << 3;

        private final int side;

        public ImageViewportTouchSide(int side) {
            this.side = side;
        }

        public boolean isTouchingLeft() {
            return (side & LEFT_SIDE) != 0;
        }

        public boolean isTouchingRight() {
            return (side & RIGHT_SIDE) != 0;
        }

        public boolean isTouchingTop() {
            return (side & TOP_SIDE) != 0;
        }

        public boolean isTouchingBottom() {
            return (side & BOTTOM_SIDE) != 0;
        }

        public boolean isTouchingAllSides() {
            return side == (LEFT_SIDE | RIGHT_SIDE | TOP_SIDE | BOTTOM_SIDE);
        }
    }

    public interface Callback {
        void onReady();

        void onError(Exception e, boolean wasInitial);
    }
}
