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
import android.util.AttributeSet;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

public class LoadingBar extends View {
    private static final float MINIMUM_PROGRESS = 0.1f;

    private float progress;
    private float targetProgress;
    private Paint paint;

    public LoadingBar(Context context) {
        super(context);
        init();
    }

    public LoadingBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoadingBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setProgress(float targetProgress) {
        float clampedProgress = Math.min(Math.max(targetProgress, 0f), 1f);
        this.targetProgress = MINIMUM_PROGRESS + clampedProgress * (1f - MINIMUM_PROGRESS);
        if (this.targetProgress < this.progress) {
            this.progress = this.targetProgress;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        progress += (targetProgress - progress) * 0.05f;

        if (progress > 0f) {
            canvas.drawRect(0f, 0f, getWidth() * progress, getHeight(), paint);
        }

        if ((getWidth() * Math.abs(targetProgress - progress)) > 1f) {
            invalidate();
        }
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(AndroidUtils.getAttrColor(getContext(), R.attr.colorAccent));
    }
}
