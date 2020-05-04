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
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;

/**
 * A RecyclerView with a GridLayoutManager that manages the span count by dividing the width of the
 * view with the value set by the maxSpanWidth attribute.
 */
public class GridRecyclerView
        extends RecyclerView {
    private GridLayoutManager gridLayoutManager;
    private float spanWidth;
    private int realSpanWidth;

    public GridRecyclerView(Context context) {
        this(context, null);
    }

    public GridRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GridRecyclerView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        gridLayoutManager = new GridLayoutManager(getContext(), 3);
        setLayoutManager(gridLayoutManager);
        setHasFixedSize(true);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.GridRecyclerView);
        try {
            spanWidth = a.getDimension(R.styleable.GridRecyclerView_spanWidth, Float.MAX_VALUE);
        } finally {
            a.recycle();
        }
    }

    public int getRealSpanWidth() {
        return realSpanWidth;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int spanCount = Math.max(1, (int) (getMeasuredWidth() / spanWidth));
        realSpanWidth = getMeasuredWidth() / spanCount;
        gridLayoutManager.setSpanCount(spanCount);
    }
}
