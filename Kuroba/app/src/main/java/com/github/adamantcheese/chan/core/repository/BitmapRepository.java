package com.github.adamantcheese.chan.core.repository;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.adamantcheese.chan.R;

public class BitmapRepository {

    public static Bitmap youtubeIcon;
    public static Bitmap stickyIcon;
    public static Bitmap closedIcon;
    public static Bitmap trashIcon;
    public static Bitmap archivedIcon;
    public static Bitmap partyHat;
    public static Bitmap error;

    public static void initialize(Context context) {
        youtubeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.youtube_icon);
        stickyIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.sticky_icon);
        closedIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_icon);
        trashIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.trash_icon);
        archivedIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.archived_icon);
        partyHat = BitmapFactory.decodeResource(context.getResources(), R.drawable.partyhat);
        error = BitmapFactory.decodeResource(context.getResources(), R.drawable.error_icon);
    }
}
