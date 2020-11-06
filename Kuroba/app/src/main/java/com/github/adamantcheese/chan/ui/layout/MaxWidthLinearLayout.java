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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.github.adamantcheese.chan.R;

public class MaxWidthLinearLayout
        extends LinearLayout {
    private final float maxWidth;

    public MaxWidthLinearLayout(Context context) {
        this(context, null);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MaxWidthLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout);
        try {
            maxWidth = a.getDimension(R.styleable.MaxWidthLinearLayout_maxWidth, Integer.MAX_VALUE);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getSize(widthMeasureSpec) > maxWidth) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec((int) maxWidth, MeasureSpec.getMode(widthMeasureSpec));
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
