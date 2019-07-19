package com.github.adamantcheese.chan.utils;

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.Theme;

public class AnimationUtils {

    public static AnimationDrawable createAnimatedDownloadIconWithThemeTextPrimaryColor(
            Context context,
            Theme theme) {
        return createAnimatedDownloadIcon(context, theme.textPrimary);
    }

    public static AnimationDrawable createAnimatedDownloadIcon(
            Context context,
            @ColorInt @Nullable Integer tintColor) {
        AnimationDrawable downloadAnimation = new AnimationDrawable();
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download0, tintColor), 200);
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download1, tintColor), 200);
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download2, tintColor), 200);
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download3, tintColor), 200);
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download4, tintColor), 200);
        downloadAnimation.addFrame(loadAnimationFrameWithTint(context, R.drawable.ic_download5, tintColor), 200);

        return downloadAnimation;
    }

    private static Drawable loadAnimationFrameWithTint(
            Context context,
            @DrawableRes int drawableId,
            @ColorInt @Nullable Integer tintColor) {
        Drawable drawable = context.getDrawable(drawableId).mutate();

        if (tintColor != null) {
            drawable.setTint(tintColor);
        }

        return drawable;
    }
}
