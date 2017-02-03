/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.helper;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import org.floens.chan.R;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.AndroidUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    public static String getTitle(Post post, Loadable loadable) {
        if (post != null) {
            if (!TextUtils.isEmpty(post.subject)) {
                return post.subject;
            } else if (!TextUtils.isEmpty(post.comment)) {
                return "/" + post.board + "/ \u2013 " + post.comment.subSequence(0, Math.min(post.comment.length(), 200)).toString();
            } else {
                return "/" + post.board + "/" + post.no;
            }
        } else if (loadable != null) {
            if (loadable.mode == Loadable.Mode.CATALOG) {
                return "/" + loadable.board + "/";
            } else {
                return "/" + loadable.board + "/" + loadable.no;
            }
        } else {
            return "";
        }
    }

    private static SimpleDateFormat dateFormat = new SimpleDateFormat("LL/dd/yy(EEE)HH:mm:ss", Locale.US);
    private static Date tmpDate = new Date();

    public static String getLocalDate(Post post) {
        tmpDate.setTime(post.time * 1000L);
        return dateFormat.format(tmpDate);
    }
}
