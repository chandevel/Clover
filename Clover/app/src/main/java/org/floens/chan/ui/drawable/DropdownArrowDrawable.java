package org.floens.chan.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import static org.floens.chan.utils.AndroidUtils.dp;

public class DropdownArrowDrawable extends Drawable {
    private Paint paint = new Paint();
    private Path path = new Path();
    private int width;
    private int height;

    public DropdownArrowDrawable() {
        width = dp(12);
        height = dp(6);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffffffff);

        path.moveTo(0, 0);
        path.lineTo(width, 0);
        path.lineTo(width / 2, height);
        path.lineTo(0, 0);
        path.close();
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public int getIntrinsicWidth() {
        return width;
    }

    @Override
    public int getIntrinsicHeight() {
        return height;
    }

    @Override
    protected boolean onStateChange(int[] states) {
        boolean pressed = false;
        for (int state : states) {
            if (state == android.R.attr.state_pressed) {
                pressed = true;
            }
        }
        int color = pressed ? 0x88ffffff : 0xffffffff;
        if (color != paint.getColor()) {
            paint.setColor(color);
            invalidateSelf();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
