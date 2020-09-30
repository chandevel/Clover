package com.github.adamantcheese.chan.core.repository;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.DrawableRes;

import com.github.adamantcheese.chan.R;

public class BitmapRepository {

    @SuppressLint("StaticFieldLeak")
    private static Context context;

    public static Bitmap youtubeIcon;
    public static Bitmap streamableIcon;
    public static Bitmap clypIcon;

    public static Bitmap stickyIcon;
    public static Bitmap closedIcon;
    public static Bitmap trashIcon;
    public static Bitmap archivedIcon;
    public static Bitmap partyHat;
    public static Bitmap error;

    public static void initialize(Context c) {
        context = c;

        youtubeIcon = decode(R.drawable.youtube_icon);
        streamableIcon = decode(R.drawable.streamable_icon);
        clypIcon = decode(R.drawable.clyp_icon);

        stickyIcon = decode(R.drawable.sticky_icon);
        closedIcon = decode(R.drawable.closed_icon);
        trashIcon = decode(R.drawable.trash_icon);
        archivedIcon = decode(R.drawable.archived_icon);
        partyHat = decode(R.drawable.partyhat);
        error = decode(R.drawable.error_icon);

        context = null; // to prevent leaks
    }

    private static Bitmap decode(@DrawableRes int resId) {
        return BitmapFactory.decodeResource(context.getResources(), resId);
    }
}
