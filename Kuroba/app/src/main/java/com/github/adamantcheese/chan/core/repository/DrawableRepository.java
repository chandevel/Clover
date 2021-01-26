package com.github.adamantcheese.chan.core.repository;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;

import com.github.adamantcheese.chan.R;

public class DrawableRepository {
    public static Drawable playIcon;

    @SuppressLint("UseCompatLoadingForDrawables")
    public static void initialize(Context c) {
        playIcon = c.getDrawable(R.drawable.ic_fluent_play_circle_24_regular);
    }
}
