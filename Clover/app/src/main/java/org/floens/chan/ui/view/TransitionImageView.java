package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class TransitionImageView extends View {
    private static final String TAG = "TransitionImageView";

    private Bitmap bitmap;
    private Matrix matrix = new Matrix();
    private Paint paint = new Paint();
    private RectF bitmapRect = new RectF();
    private RectF destRect = new RectF();
    private RectF sourceImageRect = new RectF();
    private PointF sourceOverlap = new PointF();
    private RectF destClip = new RectF();
    private float progress;
    private float stateScale;
    private float stateBitmapScaleDiff;
    private PointF stateBitmapSize;
    private PointF statePos;

    public TransitionImageView(Context context) {
        super(context);
        init();
    }

    public TransitionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TransitionImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Center inside method
        float selfWidth = getWidth();
        float selfHeight = getHeight();

        float destScale = Math.min(
                selfWidth / (float) bitmap.getWidth(),
                selfHeight / (float) bitmap.getHeight());

        RectF output = new RectF(
                (selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                (selfHeight - bitmap.getHeight() * destScale) * 0.5f, 0, 0);

        output.right = bitmap.getWidth() * destScale + output.left;
        output.bottom = bitmap.getHeight() * destScale + output.top;

        destRect.set(output);

        matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);
    }

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
        float scale = Math.max(
                (float) viewSize.x / (float) bitmap.getWidth(),
                (float) viewSize.y / (float) bitmap.getHeight());
        float scaledX = bitmap.getWidth() * scale;
        float scaledY = bitmap.getHeight() * scale;
        float offsetX = (scaledX - viewSize.x) * 0.5f;
        float offsetY = (scaledY - viewSize.y) * 0.5f;

        sourceOverlap.set(offsetX, offsetY);

        sourceImageRect.set(
                -offsetX + globalOffsetX,
                -offsetY + globalOffsetY,
                scaledX - offsetX + globalOffsetX,
                scaledY - offsetY + globalOffsetY);
    }

    public void setState(float stateScale, PointF statePos, PointF stateBitmapSize) {
        this.stateScale = stateScale;
        this.statePos = statePos;
        this.stateBitmapSize = stateBitmapSize;
    }

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

            float destScale = Math.min(
                    selfWidth / (float) bitmap.getWidth(),
                    selfHeight / (float) bitmap.getHeight());

            output = new RectF(
                    (selfWidth - bitmap.getWidth() * destScale) * 0.5f,
                    (selfHeight - bitmap.getHeight() * destScale) * 0.5f, 0, 0);

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

        destClip.set(
                output.left + sourceOverlap.x * (1f - progress),
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

    private void init() {
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
    }

    private float lerp(float a, float b, float x) {
        return a + (b - a) * x;
    }
}
