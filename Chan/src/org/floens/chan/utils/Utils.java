package org.floens.chan.utils;

import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

public class Utils {
    @SuppressWarnings("deprecation")
    public static void setPressedDrawable(View view) {
        TypedArray arr = view.getContext().obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackground});
        
        Drawable drawable = arr.getDrawable(0);
        
        view.setBackgroundDrawable(drawable);
        
        arr.recycle();
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
