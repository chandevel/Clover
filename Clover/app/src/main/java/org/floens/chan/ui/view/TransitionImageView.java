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

    public void setSourceImageView(Point windowLocation, Point viewSize, Bitmap bitmap) {
        this.bitmap = bitmap;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());

        int[] myLoc = new int[2];
        getLocationInWindow(myLoc);
        float globalOffsetX = windowLocation.x - myLoc[0];
        float globalOffsetY = windowLocation.y - myLoc[1];

        // Get the coords in the image view with the center crop method
        float scaleX = (float) viewSize.x / (float) bitmap.getWidth();
        float scaleY = (float) viewSize.y / (float) bitmap.getHeight();
        float scale = scaleX > scaleY ? scaleX : scaleY;
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

    public void setProgress(float progress) {
        this.progress = progress;

        // Center inside method
        float destScale = Math.min(
                (float) getWidth() / (float) bitmap.getWidth(),
                (float) getHeight() / (float) bitmap.getHeight());
        float destOffsetX = (getWidth() - bitmap.getWidth() * destScale) * 0.5f;
        float destOffsetY = (getHeight() - bitmap.getHeight() * destScale) * 0.5f;
        float destRight = bitmap.getWidth() * destScale + destOffsetX;
        float destBottom = bitmap.getHeight() * destScale + destOffsetY;

        float left = sourceImageRect.left + (destOffsetX - sourceImageRect.left) * progress;
        float top = sourceImageRect.top + (destOffsetY - sourceImageRect.top) * progress;
        float right = sourceImageRect.right + (destRight - sourceImageRect.right) * progress;
        float bottom = sourceImageRect.bottom + (destBottom - sourceImageRect.bottom) * progress;

        destRect.set(left, top, right, bottom);

        destClip.set(
                left + sourceOverlap.x * (1f - progress),
                top + sourceOverlap.y * (1f - progress),
                right - sourceOverlap.x * (1f - progress),
                bottom - sourceOverlap.y * (1f - progress)
        );

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap != null) {
            matrix.setRectToRect(bitmapRect, destRect, Matrix.ScaleToFit.FILL);
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
}
