package com.github.adamantcheese.chan.ui.view;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {
    public static FastScroller create(RecyclerView recyclerView) {
        StateListDrawable thumb = getThumb(recyclerView.getContext());
        StateListDrawable track = getTrack(recyclerView.getContext());

        final int defaultThickness = (int) dp(4);
        final int targetWidth = (int) dp(8);
        final int minimumRange = (int) dp(50);
        final int margin = (int) dp(0);
        final int thumbMinLength = (int) dp(23);

        return new FastScroller(recyclerView,
                thumb,
                track,
                thumb,
                track,
                defaultThickness,
                minimumRange,
                margin,
                thumbMinLength,
                targetWidth
        );
    }

    private static StateListDrawable getThumb(Context context) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed},
                new ColorDrawable(getAttrColor(context, R.attr.colorAccent))
        );
        list.addState(new int[]{}, new ColorDrawable(getAttrColor(context, android.R.attr.textColorSecondary)));
        return list;
    }

    private static StateListDrawable getTrack(Context context) {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed},
                new ColorDrawable(getAttrColor(context, android.R.attr.textColorHint))
        );
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }
}
