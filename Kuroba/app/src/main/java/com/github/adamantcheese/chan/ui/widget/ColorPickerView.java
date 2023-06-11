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

import static android.graphics.Color.*;
import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Shader.TileMode.REPEAT;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.repository.BitmapRepository;

public class ColorPickerView
        extends View {
    private static final int[] COLORS = new int[]{RED, MAGENTA, BLUE, CYAN, GREEN, YELLOW, RED};
    private final RectF ovalRect = new RectF();

    private final Paint paint;
    private final Paint centerTransPaint;
    private final Paint centerPaint;
    private float centerRadius;

    public ColorPickerView(Context context) {
        this(context, null);
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        paint = new Paint(ANTI_ALIAS_FLAG);
        paint.setShader(new SweepGradient(0, 0, COLORS, null));
        paint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint(ANTI_ALIAS_FLAG);

        if (isInEditMode()) {
            BitmapRepository.initialize(getContext());
            setColor(GREEN & 0x7FFFFFFF);
        }

        centerTransPaint = new Paint(ANTI_ALIAS_FLAG);
        centerTransPaint.setShader(new BitmapShader(BitmapRepository.transparentCheckerboard, REPEAT, REPEAT));
    }

    public void setColor(int color) {
        centerPaint.setColor(color);
        invalidate();
    }

    public int getColor() {
        return centerPaint.getColor();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxSize = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        centerRadius = maxSize / 4f;
        paint.setStrokeWidth(maxSize / 4f);
        setMeasuredDimension(maxSize, maxSize);
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
            int newColor = interpColor(unit);
            // deal with a possible alpha that was set
            int alphaColor = Color.argb(
                    Color.alpha(centerPaint.getColor()),
                    Color.red(newColor),
                    Color.green(newColor),
                    Color.blue(newColor)
            );
            centerPaint.setColor(alphaColor);
            invalidate();
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = Math.min(getWidth() / 2f, getHeight() / 2f) - paint.getStrokeWidth() * 0.5f;
        ovalRect.set(-r, -r, r, r);
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.drawRect(-centerRadius, -centerRadius, centerRadius, centerRadius, centerTransPaint);
        canvas.drawOval(ovalRect, paint);
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
        int a = ave(alpha(c0), alpha(c1), p);
        int r = ave(red(c0), red(c1), p);
        int g = ave(green(c0), green(c1), p);
        int b = ave(blue(c0), blue(c1), p);

        return argb(a, r, g, b);
    }

    private int ave(int s, int d, float p) {
        return s + Math.round(p * (d - s));
    }
}
