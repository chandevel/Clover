package org.floens.chan.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class PopControllerTransition extends ControllerTransition {
    @Override
    public void perform() {
        Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, to.view.getAlpha(), 1f);
        toAlpha.setInterpolator(new DecelerateInterpolator()); // new PathInterpolator(0f, 0f, 0.2f, 1f)
        toAlpha.setDuration(250);

        Animator fromY = ObjectAnimator.ofFloat(from.view, View.Y, 0f, from.view.getHeight() * 0.05f);
        fromY.setInterpolator(new AccelerateInterpolator(2.5f));
        fromY.setDuration(250);

        fromY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onCompleted();
            }
        });

        Animator fromAlpha = ObjectAnimator.ofFloat(from.view, View.ALPHA, from.view.getAlpha(), 0f);
        fromAlpha.setInterpolator(new AccelerateInterpolator(2f));
        fromAlpha.setStartDelay(100);
        fromAlpha.setDuration(150);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(toAlpha, fromY, fromAlpha);
        set.start();
    }
}
