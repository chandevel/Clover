package com.github.adamantcheese.chan.core.repository;

import android.content.Context;
import android.graphics.Bitmap;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.BitmapUtils.decode;

public class BitmapRepository {
    public static Bitmap youtubeIcon;
    public static Bitmap streamableIcon;
    public static Bitmap clypIcon;
    public static Bitmap bandcampIcon;
    public static Bitmap soundcloudIcon;
    public static Bitmap shadertoyIcon;
    public static Bitmap vocarooIcon;

    public static Bitmap stickyIcon;
    public static Bitmap closedIcon;
    public static Bitmap trashIcon;
    public static Bitmap archivedIcon;
    public static Bitmap error;

    public static Bitmap partyHat;
    public static Bitmap santaHat;

    public static void initialize(Context c) {
        youtubeIcon = decode(c, R.drawable.youtube_icon);
        streamableIcon = decode(c, R.drawable.streamable_icon);
        clypIcon = decode(c, R.drawable.clyp_icon);
        bandcampIcon = decode(c, R.drawable.bandcamp_icon);
        soundcloudIcon = decode(c, R.drawable.soundcloud_icon);
        shadertoyIcon = decode(c, R.drawable.shadertoy_icon);
        vocarooIcon = decode(c, R.drawable.vocaroo_icon);

        stickyIcon = decode(c, R.drawable.sticky_icon);
        closedIcon = decode(c, R.drawable.closed_icon);
        trashIcon = decode(c, R.drawable.trash_icon);
        archivedIcon = decode(c, R.drawable.archived_icon);
        error = decode(c, R.drawable.error_icon);

        partyHat = decode(c, R.drawable.partyhat);
        santaHat = decode(c, R.drawable.santahat);
    }
}
