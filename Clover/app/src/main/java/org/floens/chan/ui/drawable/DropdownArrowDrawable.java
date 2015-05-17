package org.floens.chan.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class DropdownArrowDrawable extends Drawable {
    private Paint paint = new Paint();
    private Path path = new Path();
    private int width;
    private int height;
    private float rotation;
    private int color;
    private int pressedColor;

    public DropdownArrowDrawable(int width, int height, boolean down, int color, int pressedColor) {
        this.width = width;
        this.height = height;
        rotation = down ? 0f : 1f;
        this.color = color;
        this.pressedColor = pressedColor;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    @Override
    public void draw(Canvas canvas) {
        path.rewind();
        path.moveTo(0, height / 2);
        path.lineTo(width, height / 2);
        path.lineTo(width / 2, height);
        path.lineTo(0, height / 2);
        path.close();

        canvas.save();
        canvas.rotate(rotation * 180f, width / 2f, height / 2f);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
        invalidateSelf();
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
        int color = pressed ? pressedColor : this.color;
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
