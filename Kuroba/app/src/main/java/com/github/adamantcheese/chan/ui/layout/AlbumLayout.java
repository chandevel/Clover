package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.GridLayoutManager;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.view.GridRecyclerView;

public class AlbumLayout
        extends GridRecyclerView {

    int spanCount = 3;

    public AlbumLayout(Context context) {
        this(context, null);
    }

    public AlbumLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlbumLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        int gridCountSetting = !isInEditMode() ? ChanSettings.albumGridSpanCount.get() : 3;
        if (gridCountSetting > 0) {
            // Set count
            spanCount = gridCountSetting;
        } else {
            // Auto
            spanCount = Math.max(1, Math.round((float) getMeasuredWidth() / getSpanWidth()));
        }
        setRealSpanWidth(getMeasuredWidth() / spanCount);

        if (getLayoutManager() != null) {
            ((GridLayoutManager) getLayoutManager()).setSpanCount(spanCount);
        }
    }
}
