package com.github.adamantcheese.chan.core.repository;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

public class BitmapRepository {

    public Bitmap youtubeIcon;
    public Bitmap stickyIcon;
    public Bitmap closedIcon;
    public Bitmap trashIcon;
    public Bitmap archivedIcon;
    public Bitmap partyHat;
    public Bitmap error;

    public void initialize() {
        youtubeIcon = BitmapFactory.decodeResource(getRes(), R.drawable.youtube_icon);
        stickyIcon = BitmapFactory.decodeResource(getRes(), R.drawable.sticky_icon);
        closedIcon = BitmapFactory.decodeResource(getRes(), R.drawable.closed_icon);
        trashIcon = BitmapFactory.decodeResource(getRes(), R.drawable.trash_icon);
        archivedIcon = BitmapFactory.decodeResource(getRes(), R.drawable.archived_icon);
        partyHat = BitmapFactory.decodeResource(getRes(), R.drawable.partyhat);
        error = BitmapFactory.decodeResource(getRes(), R.drawable.error_icon);
    }
}
