package com.github.adamantcheese.chan.utils;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;

import androidx.annotation.ColorInt;

import com.github.adamantcheese.chan.R;

public class AnimationUtils {
    public static AnimatedVectorDrawable createAnimatedDownloadIcon(
            Context context,
            @ColorInt int tintColor) {
        AnimatedVectorDrawable drawable = (AnimatedVectorDrawable) context.getDrawable(R.drawable.ic_download_anim);
        drawable.setTint(tintColor);
        return drawable;
    }
}
