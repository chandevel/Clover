package org.floens.chan.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class FadeInTransition extends ControllerTransition {
    @Override
    public void perform() {
        Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, 0f, 1f);
        toAlpha.setDuration(200);
        toAlpha.setInterpolator(new AccelerateDecelerateInterpolator());

        toAlpha.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onCompleted();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(toAlpha);
        set.start();
    }
}
