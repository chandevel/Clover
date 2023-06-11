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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.utils.AnimationUtils;

public class MulticolorBarView
        extends View {

    private float progress = 0.0f;
    private int[] colors = {};
    private final boolean renderVertical;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MulticolorBarView(Context context) {
        this(context, null);
    }

    public MulticolorBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MulticolorBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MulticolorBarView);
        try {
            renderVertical = a.getBoolean(R.styleable.MulticolorBarView_vertical, false);
        } finally {
            a.recycle();
        }

        if (isInEditMode()) {
            colors = AnimationUtils.RAINBOW_COLORS;
            progress = 0.5f;
        }
    }

    public void setColors(int[] colors) {
        this.colors = colors;
        invalidate();
        requestLayout();
    }

    public void setProgress(float updatedProgress) {
        progress = updatedProgress;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (colors.length == 0) return;
        int translationStep = (int) (((renderVertical ? getHeight() : getWidth()) * progress) / colors.length);
        for (int i = 0; i < colors.length; i++) {
            paint.setColor(colors[i]);
            if (renderVertical) {
                canvas.drawRect(0f, i * translationStep, getWidth(), (i + 1) * translationStep, paint);
            } else {
                canvas.drawRect(i * translationStep, 0f, (i + 1) * translationStep, getHeight(), paint);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (colors.length == 0) {
            if (renderVertical) {
                setMeasuredDimension(0, getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec));
            } else {
                setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), 0);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
