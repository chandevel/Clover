package com.github.adamantcheese.chan.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.FrameLayout;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class LayoutUtils {
    public static View inflate(Context context, int resId, ViewGroup root) {
        return LayoutInflater.from(context).inflate(resId, root);
    }

    public static View inflate(Context context, int resId, ViewGroup root, boolean attachToRoot) {
        return LayoutInflater.from(context).inflate(resId, root, attachToRoot);
    }

    public static ViewGroup inflate(Context context, int resId) {
        return (ViewGroup) LayoutInflater.from(context).inflate(resId, null);
    }

    public static int measureContentWidth(Context context, Adapter adapter, int minimumSizePx) {
        ViewGroup mMeasureParent = new FrameLayout(context);
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < adapter.getCount(); i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            itemView = adapter.getView(i, itemView, mMeasureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);

            final int itemWidth = itemView.getMeasuredWidth();

            if (itemWidth > maxWidth) {
                maxWidth = itemWidth;
            }
        }

        return Math.max(maxWidth, minimumSizePx);
    }
}
