/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import static org.floens.chan.utils.AndroidUtils.sp;

public class FastTextView extends View {
    private TextPaint paint;

    private CharSequence text;

    private boolean update = false;
    private StaticLayout layout;

    public FastTextView(Context context) {
        super(context);
        init();
    }

    public FastTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FastTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setText(CharSequence text) {
        if (!TextUtils.equals(this.text, text)) {
            this.text = text;

            if (text == null) {
                layout = null;
            } else {
                update = true;
            }
        }
    }

    public void setTextSize(float size) {
        int sizeSp = sp(size);
        if (paint.getTextSize() != sizeSp) {
            paint.setTextSize(sizeSp);
            update = true;
        }
    }

    public void setTextColor(int color) {
        if (paint.getColor() != color) {
            paint.setColor(color);
            update = true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        update = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (update) {
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            layout = new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            update = false;
        }

        if (layout != null) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            layout.draw(canvas);
            canvas.restore();
        }
    }
}
