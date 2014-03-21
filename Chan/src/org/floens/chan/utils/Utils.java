package org.floens.chan.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

public class Utils {
    /**
     * Sets the android.R.attr.selectableItemBackground as background drawable on the view.
     * @param view
     */
    @SuppressWarnings("deprecation")
    public static void setPressedDrawable(View view) {
        Drawable drawable = Utils.getSelectableBackgroundDrawable(view.getContext());
        view.setBackgroundDrawable(drawable);
    }

    public static Drawable getSelectableBackgroundDrawable(Context context) {
        TypedArray arr = context.obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackground});

        Drawable drawable = arr.getDrawable(0);

        arr.recycle();

        return drawable;
    }

    /**
     * Causes the runnable to be added to the message queue.
     * The runnable will be run on the ui thread.
     * @param runnable
     */
    public static void runOnUiThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
