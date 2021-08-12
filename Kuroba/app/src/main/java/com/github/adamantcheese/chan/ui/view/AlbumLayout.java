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
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

/**
 * A RecyclerView with a GridLayoutManager or StaggeredGridLayoutManager that manages the span count by
 * dividing the width of the view with the value set by the spanWidth attribute if in automatic mode.
 * <p>
 * The span count is determined by getAlbumColumnCount and staggering is set with useStaggeredAlbumGrid.
 */
public class AlbumLayout
        extends RecyclerView {
    private final GridLayoutManager gridLayoutManager;
    private final StaggeredGridLayoutManager staggeredGridLayoutManager;
    private final float xmlSpanWidth;
    private int measuredSpanWidth;

    public AlbumLayout(Context context) {
        this(context, null);
    }

    public AlbumLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlbumLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        gridLayoutManager = new GridLayoutManager(getContext(), 3);
        staggeredGridLayoutManager = new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL);
        setLayoutManager(useGridLayout() ? gridLayoutManager : staggeredGridLayoutManager);
        setHasFixedSize(true);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.AlbumLayout);
        try {
            xmlSpanWidth = a.getDimension(R.styleable.AlbumLayout_spanWidth, Float.MAX_VALUE);
        } finally {
            a.recycle();
        }
    }

    public int getMeasuredSpanWidth() {
        return measuredSpanWidth;
    }

    public int getSpanCount() {
        if (useGridLayout()) {
            return gridLayoutManager.getSpanCount();
        } else {
            return staggeredGridLayoutManager.getSpanCount();
        }
    }

    protected void setSpanCount(int count) {
        if (useGridLayout()) {
            gridLayoutManager.setSpanCount(count);
        } else {
            staggeredGridLayoutManager.setSpanCount(count);
        }
    }

    private boolean useGridLayout() {
        return isInEditMode() || !ChanSettings.useStaggeredAlbumGrid.get();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int gridCountSetting = !isInEditMode() ? ChanSettings.getAlbumColumnCount() : 3;
        if (gridCountSetting > 0) {
            // Set count
            setSpanCount(gridCountSetting);
        } else {
            // Auto
            setSpanCount(Math.max(1, Math.round(getMeasuredWidth() / xmlSpanWidth)));
        }
        measuredSpanWidth = getMeasuredWidth() / getSpanCount();
    }
}
