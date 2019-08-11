package com.github.adamantcheese.chan.utils;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.github.adamantcheese.chan.R;

public class AnimationUtils {

    public static AnimatedVectorDrawableCompat createAnimatedDownloadIcon(
            Context context,
            @ColorInt int tintColor) {
        AnimatedVectorDrawableCompat drawable = AnimatedVectorDrawableCompat.create(
                context,
                R.drawable.ic_download_anim);
        drawable.setTint(tintColor);

        return drawable;
    }

}
