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

import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.Nullable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Loadable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostHelper {
    public static CharSequence prependIcon(CharSequence total, BitmapDrawable bitmapDrawable, int height) {
        SpannableString string = new SpannableString("  ");
        ImageSpan imageSpan = new ImageSpan(bitmapDrawable);

        int width = (int) (height / (bitmapDrawable.getIntrinsicHeight() / (float) bitmapDrawable.getIntrinsicWidth()));

        imageSpan.getDrawable().setBounds(0, 0, width, height);
        string.setSpan(imageSpan, 0, 1, 0);
        if (total == null) {
            return string;
        } else {
            return TextUtils.concat(string, " ", total);
        }
    }

    public static String getTitle(@Nullable Post post, @Nullable Loadable loadable) {
        if (post != null) {
            if (!TextUtils.isEmpty(post.subject)) {
                return post.subject;
            } else if (!TextUtils.isEmpty(post.comment)) {
                return "/" + post.boardId + "/ \u2013 " + post.comment.subSequence(0, Math.min(post.comment.length(), 200)).toString();
            } else {
                return "/" + post.boardId + "/" + post.no;
            }
        } else if (loadable != null) {
            if (loadable.mode == Loadable.Mode.CATALOG) {
                return "/" + loadable.boardCode + "/";
            } else {
                return "/" + loadable.boardCode + "/" + loadable.no;
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
