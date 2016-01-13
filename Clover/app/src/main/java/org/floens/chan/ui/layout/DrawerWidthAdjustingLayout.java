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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.support.v4.widget.DrawerLayout;
import android.util.AttributeSet;
import android.view.View;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;

public class DrawerWidthAdjustingLayout extends DrawerLayout {
    public DrawerWidthAdjustingLayout(Context context) {
        super(context);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
//        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
//        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        View drawer = findViewById(R.id.drawer);

        int width = Math.min(widthSize - dp(56), dp(56) * 6);
        if (drawer.getLayoutParams().width != width) {
            drawer.getLayoutParams().width = width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
