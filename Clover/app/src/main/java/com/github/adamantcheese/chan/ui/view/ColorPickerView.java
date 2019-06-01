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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import androidx.annotation.NonNull;
import android.view.MotionEvent;
import android.view.View;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class ColorPickerView extends View {
    private static final int[] COLORS = new int[]{
            0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00,
            0xFFFFFF00, 0xFFFF0000
    };

    private Paint paint;
    private Paint centerPaint;
    private int centerRadius;

    public ColorPickerView(Context context) {
        super(context);

        centerRadius = dp(32);

        Shader s = new SweepGradient(0, 0, COLORS, null);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(s);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(32));

        centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setStrokeWidth(dp(5));
    }

    public void setColor(int color) {
        centerPaint.setColor(color);
    }

    public int getColor() {
        return centerPaint.getColor();
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        float x = event.getX() - getWidth() / 2f;
        float y = event.getY() - getHeight() / 2f;

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float angle = (float) Math.atan2(y, x);
            // need to turn angle [-PI ... PI] into unit [0....1]
            float unit = (float) (angle / (2.0 * Math.PI));
            if (unit < 0.0) {
                unit += 1.0;
            }
            centerPaint.setColor(interpColor(unit));
            invalidate();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Math.min(getWidth() / 2f, getHeight() / 2f) - paint.getStrokeWidth() * 0.5f;
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.drawOval(new RectF(-r, -r, r, r), paint);
        canvas.drawCircle(0, 0, centerRadius, centerPaint);
    }

    private int interpColor(float unit) {
        if (unit <= 0) {
            return ColorPickerView.COLORS[0];
        }
        if (unit >= 1) {
            return ColorPickerView.COLORS[ColorPickerView.COLORS.length - 1];
        }

        float p = unit * (ColorPickerView.COLORS.length - 1);
        int i = (int) p;
        p -= i;

        // now p is just the fractional part [0...1) and i is the index
        int c0 = ColorPickerView.COLORS[i];
        int c1 = ColorPickerView.COLORS[i + 1];
        int a = ave(Color.alpha(c0), Color.alpha(c1), p);
        int r = ave(Color.red(c0), Color.red(c1), p);
        int g = ave(Color.green(c0), Color.green(c1), p);
        int b = ave(Color.blue(c0), Color.blue(c1), p);

        return Color.argb(a, r, g, b);
    }

    private int ave(int s, int d, float p) {
        return s + Math.round(p * (d - s));
    }
}
