package org.floens.chan.utils;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.View;

public class ViewUtils {
    @SuppressWarnings("deprecation")
    public static void setPressedDrawable(View view) {
        TypedArray arr = view.getContext().obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackground});
        
        Drawable drawable = arr.getDrawable(0);
        
        view.setBackgroundDrawable(drawable);
        
        arr.recycle();
    }
}
