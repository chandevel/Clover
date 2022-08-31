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
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class TransitionImageView
        extends View {
    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint();
    private final RectF bitmapRect = new RectF();
    private final RectF destRect = new RectF();
    private final RectF sourceImageRect = new RectF();
    private final PointF sourceOverlap = new PointF();
    private final RectF destClip = new RectF();
    private float progress;
    private float stateScale;
    private float stateBitmapScaleDiff;
    private PointF stateBitmapSize;
    private PointF statePos;

    public TransitionImageView(Context context) {
        this(context, null);
    }

    public TransitionImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransitionImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
    }

    /**
     * Sets the bitmap when switching the image without animating.
     *
     * @param bitmap The source bitmap to animate from.
     */
    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Center inside method
        float selfWidth = getWidth();
        float selfHeight = getHeight();

        float destScale = Math.min(selfWidth / (float) bitmap.getWidth(), selfHeight / (float) bitmap.getHeight());

        RectF output = new RectF((selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                (selfHeight - bitmap.getHeight() * destScale) * 0.5f,
                0,
                0
        );

        output.right = bitmap.getWidth() * destScale + output.left;
        output.bottom = bitmap.getHeight() * destScale + output.top;

        destRect.set(output);

        matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);
    }

    /**
     * Set the target image view items to animate to/from when animating in/out.
     *
     * @param windowLocation The location of the view in the window.
     * @param viewSize       The width and height of the view.
     * @param bitmap         The bitmap in the view, or an empty bitmap.
     */
    public void setSourceImageView(Point windowLocation, Point viewSize, Bitmap bitmap) {
        this.bitmap = bitmap;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        if (stateBitmapSize != null) {
            stateBitmapScaleDiff = stateBitmapSize.x / bitmap.getWidth();
        }

        int[] myLoc = new int[2];
        getLocationInWindow(myLoc);
        float globalOffsetX = windowLocation.x - myLoc[0];
        float globalOffsetY = windowLocation.y - myLoc[1];

        // Get the coords in the image view with the center crop method
        float scale = Math.max((float) viewSize.x / (float) bitmap.getWidth(),
                (float) viewSize.y / (float) bitmap.getHeight()
        );
        float scaledX = bitmap.getWidth() * scale;
        float scaledY = bitmap.getHeight() * scale;
        float offsetX = (scaledX - viewSize.x) * 0.5f;
        float offsetY = (scaledY - viewSize.y) * 0.5f;

        sourceOverlap.set(offsetX, offsetY);

        sourceImageRect.set(-offsetX + globalOffsetX,
                -offsetY + globalOffsetY,
                scaledX - offsetX + globalOffsetX,
                scaledY - offsetY + globalOffsetY
        );
    }

    /**
     * When animating in/out from a CustomScaleImageView, you'll need to take into account some parameters.
     *
     * @param stateScale      The current scale of the image.
     * @param statePos        The source coordinate of the image's top right corner (0,0).
     * @param stateBitmapSize The CustomScaleImageView's source width and height.
     */
    public void setState(float stateScale, PointF statePos, PointF stateBitmapSize) {
        this.stateScale = stateScale;
        this.statePos = statePos;
        this.stateBitmapSize = stateBitmapSize;
    }

    /**
     * Set the progress for animating between the two views.
     * If statePos is not null, we are animating out from a specified CustomScaleImageView state
     * If statePos is null, we are animating in (usually) or out from a non-stateful item (like a thumbnail)
     *
     * @param progress The current animation progress
     */
    public void setProgress(float progress) {
        this.progress = progress;

        RectF output;
        if (statePos != null) {
            // Use scale and translate from ssiv
            output = new RectF(-statePos.x * stateScale, -statePos.y * stateScale, 0, 0);
            output.right = output.left + bitmap.getWidth() * stateBitmapScaleDiff * stateScale;
            output.bottom = output.top + bitmap.getHeight() * stateBitmapScaleDiff * stateScale;
        } else {
            // Center inside method
            float selfWidth = getWidth();
            float selfHeight = getHeight();

            float destScale = Math.min(selfWidth / (float) bitmap.getWidth(), selfHeight / (float) bitmap.getHeight());

            output = new RectF((selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                    (selfHeight - bitmap.getHeight() * destScale) * 0.5f,
                    0,
                    0
            );

            output.right = bitmap.getWidth() * destScale + output.left;
            output.bottom = bitmap.getHeight() * destScale + output.top;
        }

        // Linear interpolate between start bounds and calculated final bounds
        output.left = lerp(sourceImageRect.left, output.left, progress);
        output.top = lerp(sourceImageRect.top, output.top, progress);
        output.right = lerp(sourceImageRect.right, output.right, progress);
        output.bottom = lerp(sourceImageRect.bottom, output.bottom, progress);

        destRect.set(output);

        matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);

        destClip.set(output.left + sourceOverlap.x * (1f - progress),
                output.top + sourceOverlap.y * (1f - progress),
                output.right - sourceOverlap.x * (1f - progress),
                output.bottom - sourceOverlap.y * (1f - progress)
        );

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap != null) {
            canvas.save();
            if (progress < 1f) {
                canvas.clipRect(destClip);
            }
            canvas.drawBitmap(bitmap, matrix, paint);
            canvas.restore();
        }
    }

    private float lerp(float a, float b, float x) {
        return a + (b - a) * x;
    }
}
