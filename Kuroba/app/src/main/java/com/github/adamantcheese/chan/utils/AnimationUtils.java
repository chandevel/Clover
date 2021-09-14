package com.github.adamantcheese.chan.utils;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;

import static android.graphics.Color.BLUE;
import static android.graphics.Color.CYAN;
import static android.graphics.Color.GREEN;
import static android.graphics.Color.MAGENTA;
import static android.graphics.Color.RED;
import static android.graphics.Color.YELLOW;

public class AnimationUtils {

    public static final int[] RAINBOW_COLORS =
            {RED, 0xFF7F00, YELLOW, 0x7FFF00, GREEN, 0x00FF7F, CYAN, 0x007FFF, BLUE, 0x7F00FF, MAGENTA, 0xFF007F};

    public static void animateStatusBar(Window window, boolean in, final int originalColor, int duration) {
        ValueAnimator statusBar = ValueAnimator.ofFloat(in ? 0f : 0.5f, in ? 0.5f : 0f);
        statusBar.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            if (progress == 0f) {
                window.setStatusBarColor(originalColor);
            } else {
                int r = (int) ((1f - progress) * Color.red(originalColor));
                int g = (int) ((1f - progress) * Color.green(originalColor));
                int b = (int) ((1f - progress) * Color.blue(originalColor));
                window.setStatusBarColor(Color.argb(255, r, g, b));
            }
        });
        statusBar.setDuration(duration).setInterpolator(new LinearInterpolator());
        statusBar.start();
    }

    public static void animateViewScale(View view, boolean zoomOut, int duration) {
        ScaleAnimation scaleAnimation;
        final float normalScale = 1.0f;
        final float zoomOutScale = 0.8f;

        if (zoomOut) {
            scaleAnimation = new ScaleAnimation(
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f
            );
        } else {
            scaleAnimation = new ScaleAnimation(
                    zoomOutScale,
                    normalScale,
                    zoomOutScale,
                    normalScale,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f,
                    ScaleAnimation.RELATIVE_TO_SELF,
                    0.5f
            );
        }

        scaleAnimation.setDuration(duration);
        scaleAnimation.setFillAfter(true);
        scaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());

        view.startAnimation(scaleAnimation);
    }
}
