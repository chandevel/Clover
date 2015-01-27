package org.floens.chan.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class PushControllerTransition extends ControllerTransition {
    @Override
    public void perform() {
        Animator fromAlpha = ObjectAnimator.ofFloat(from.view, View.ALPHA, 1f, 0.7f);
        fromAlpha.setDuration(217);
        fromAlpha.setInterpolator(new AccelerateDecelerateInterpolator()); // new PathInterpolator(0.4f, 0f, 0.2f, 1f)

        Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, 0f, 1f);
        toAlpha.setDuration(200);
        toAlpha.setInterpolator(new DecelerateInterpolator(2f));

        Animator toY = ObjectAnimator.ofFloat(to.view, View.Y, to.view.getHeight() * 0.08f, 0f);
        toY.setDuration(350);
        toY.setInterpolator(new DecelerateInterpolator(2.5f));

        toY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onCompleted();
            }
        });

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fromAlpha, toAlpha, toY);
        set.start();
    }
}
