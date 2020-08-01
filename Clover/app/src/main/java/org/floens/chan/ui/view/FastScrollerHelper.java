package org.floens.chan.ui.view;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;

import androidx.recyclerview.widget.RecyclerView;

import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;

/**
 * Helper for attaching a FastScroller with the correct theme colors and default values that
 * make it look like a normal scrollbar.
 */
public class FastScrollerHelper {
    public static FastScroller create(RecyclerView recyclerView) {
        StateListDrawable thumb = getThumb();
        StateListDrawable track = getTrack();

        final int defaultThickness = dp(4);
        final int targetWidth = dp(8);
        final int minimumRange = dp(50);
        final int margin = dp(0);
        final int thumbMinLength = dp(23);

        return new FastScroller(recyclerView,
                thumb, track, thumb, track,
                defaultThickness, minimumRange, margin, thumbMinLength, targetWidth);
    }

    private static StateListDrawable getThumb() {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed},
                new ColorDrawable(theme().accentColor.color));
        list.addState(new int[]{}, new ColorDrawable(theme().textSecondary));
        return list;
    }

    private static StateListDrawable getTrack() {
        StateListDrawable list = new StateListDrawable();
        list.addState(new int[]{android.R.attr.state_pressed},
                new ColorDrawable(theme().textHint));
        list.addState(new int[]{}, new ColorDrawable(0));
        return list;
    }
}
