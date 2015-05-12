package org.floens.chan.ui.helper;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import org.floens.chan.R;
import org.floens.chan.utils.AndroidUtils;

public class PostHelper {
    public static BitmapDrawable stickyIcon;
    public static BitmapDrawable closedIcon;
    public static BitmapDrawable trashIcon;
    public static BitmapDrawable archivedIcon;

    static {
        Resources res = AndroidUtils.getRes();
        stickyIcon = new BitmapDrawable(res, BitmapFactory.decodeResource(res, R.drawable.sticky_icon));
        closedIcon = new BitmapDrawable(res, BitmapFactory.decodeResource(res, R.drawable.closed_icon));
        trashIcon = new BitmapDrawable(res, BitmapFactory.decodeResource(res, R.drawable.trash_icon));
        archivedIcon = new BitmapDrawable(res, BitmapFactory.decodeResource(res, R.drawable.archived_icon));
    }

    public static CharSequence addIcon(BitmapDrawable bitmapDrawable, int height) {
        return addIcon(null, bitmapDrawable, height);
    }

    public static CharSequence addIcon(CharSequence total, BitmapDrawable bitmapDrawable, int height) {
        SpannableString string = new SpannableString("  ");
        ImageSpan imageSpan = new ImageSpan(bitmapDrawable);

        int width = (int) (height / (bitmapDrawable.getIntrinsicHeight() / (float) bitmapDrawable.getIntrinsicWidth()));

        imageSpan.getDrawable().setBounds(0, 0, width, height);
        string.setSpan(imageSpan, 0, 1, 0);
        if (total == null) {
            return string;
        } else {
            return TextUtils.concat(total, string);
        }
    }
}
