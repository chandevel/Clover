package org.floens.chan.utils;

import org.floens.chan.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class IconCache {
    public static Bitmap stickyIcon;
    public static Bitmap closedIcon;

    /**
     * Load the icons in the cache. Lightweight icons only! Icons can be null!
     * @param context
     */
    public static void createIcons(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                stickyIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.sticky_icon);
                closedIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_icon);
            }
        }).start();
    }
}
