package org.floens.chan.ui.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import static org.floens.chan.utils.AndroidUtils.dp;


public class ThumbDrawable extends Drawable {
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private int width;
    private int height;

    public ThumbDrawable() {
        width = dp(40);
        height = dp(40);

        paint.setStrokeWidth(dp(2));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xff757575);

        path.reset();
        for (int i = 0; i < 3; i++) {
            int top = (int) (getMinimumHeight() / 2f + (i - 1) * dp(6));
            path.moveTo(dp(8), top);
            path.lineTo(getMinimumWidth() - dp(8), top);
        }
        path.moveTo(0f, 0f);
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
